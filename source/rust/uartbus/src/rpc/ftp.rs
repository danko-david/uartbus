//! File access service: serves a directory of this machine on the bus.
//!
//! Files are addressed with numbers. The number of a file is the index of its
//! path in the `.ubfs` file of the served directory (one url encoded path per
//! line, `/` is the served directory itself), so numbers survive restarts.

use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::{self, BufRead, BufReader, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, UNIX_EPOCH};

use num_bigint::BigInt;
use num_traits::ToPrimitive;

use crate::packet::PacketReader;
use crate::rpc::device::errno;
use crate::rpc::service::{
    read_param, ServiceCall, ServiceError, ServiceReply, UbService, UbString,
};
use crate::rpc::tools::{RpcType, RpcValue};
use crate::rpc::types::VUnsigned;

pub const LIST_FILE_NAME: &str = ".ubfs";

/// Largest file part that can be asked with one request.
pub const MAX_FILE_PART: usize = 4096;

pub fn url_encode(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for byte in text.bytes() {
        match byte {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'.' | b'-' | b'*' | b'_' => {
                out.push(byte as char)
            }
            b' ' => out.push('+'),
            other => out.push_str(&format!("%{other:02X}")),
        }
    }
    out
}

pub fn url_decode(text: &str) -> String {
    let bytes = text.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'+' => out.push(b' '),
            b'%' if i + 3 <= bytes.len()
                && text.is_char_boundary(i + 1)
                && text.is_char_boundary(i + 3) =>
            {
                match u8::from_str_radix(&text[i + 1..i + 3], 16) {
                    Ok(value) => {
                        out.push(value);
                        i += 2;
                    }
                    Err(_) => out.push(b'%'),
                }
            }
            other => out.push(other),
        }
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn sorted_dir_names(dir: &Path) -> io::Result<Vec<String>> {
    let mut names: Vec<String> = fs::read_dir(dir)?
        .filter_map(|entry| entry.ok())
        .map(|entry| entry.file_name().to_string_lossy().into_owned())
        .collect();
    names.sort();
    Ok(names)
}

struct FileRegistry {
    root: PathBuf,
    /// index: file number
    paths: Vec<String>,
    by_path: HashMap<String, usize>,
    list_file: File,
}

impl FileRegistry {
    fn open(root: &Path) -> io::Result<Self> {
        let list_path = root.join(LIST_FILE_NAME);
        let list_file = OpenOptions::new()
            .create(true)
            .append(true)
            .read(true)
            .open(&list_path)?;

        let mut registry = Self {
            root: root.to_path_buf(),
            paths: Vec::new(),
            by_path: HashMap::new(),
            list_file,
        };

        for line in BufReader::new(File::open(&list_path)?).lines() {
            let path = url_decode(&line?);
            registry
                .by_path
                .entry(path.clone())
                .or_insert(registry.paths.len());
            registry.paths.push(path);
        }

        if registry.paths.is_empty() {
            registry.register("/")?;
        }

        registry.discover("")?;
        Ok(registry)
    }

    fn register(&mut self, path: &str) -> io::Result<usize> {
        writeln!(self.list_file, "{}", url_encode(path))?;
        self.list_file.flush()?;
        let index = self.paths.len();
        self.paths.push(path.to_string());
        self.by_path.insert(path.to_string(), index);
        Ok(index)
    }

    /// Registers every file under the directory that has no number yet.
    fn discover(&mut self, dir: &str) -> io::Result<()> {
        for name in sorted_dir_names(&self.fs_path(if dir.is_empty() { "/" } else { dir }))? {
            let path = format!("{dir}/{name}");
            if path == format!("/{LIST_FILE_NAME}") {
                continue;
            }
            if !self.by_path.contains_key(&path) {
                self.register(&path)?;
            }
            if self.fs_path(&path).is_dir() {
                self.discover(&path)?;
            }
        }
        Ok(())
    }

    fn fs_path(&self, path: &str) -> PathBuf {
        let relative = path.trim_start_matches('/');
        if relative.is_empty() {
            self.root.clone()
        } else {
            self.root.join(relative)
        }
    }

    /// The number of the file at the path; files that appeared since the start get a number here.
    fn number_of(&mut self, path: &str) -> Result<usize, u8> {
        let path = if path.is_empty() { "/" } else { path };
        if let Some(index) = self.by_path.get(path) {
            return Ok(*index);
        }

        let hidden = path.split('/').any(|part| part == LIST_FILE_NAME);
        if hidden || !self.fs_path(path).exists() {
            return Err(errno::ENOENT);
        }

        self.register(path).map_err(|_| errno::EIO)
    }

    fn path_of(&self, file_no: &BigInt) -> Result<String, u8> {
        file_no
            .to_usize()
            .and_then(|index| self.paths.get(index))
            .cloned()
            .ok_or(errno::ENOENT)
    }
}

pub struct FtpBusService {
    registry: Mutex<FileRegistry>,
}

fn child_path(dir: &str, name: &str) -> Result<String, u8> {
    //the served directory must not be left through a file name
    if name.is_empty()
        || name == "."
        || name == ".."
        || name == LIST_FILE_NAME
        || name.contains('/')
    {
        return Err(errno::EACCES);
    }
    Ok(format!("{}/{name}", dir.trim_end_matches('/')))
}

fn can_access(path: &Path, mode: libc::c_int) -> bool {
    use std::os::unix::ffi::OsStrExt;
    match std::ffi::CString::new(path.as_os_str().as_bytes()) {
        // SAFETY: access only reads the NUL terminated path.
        Ok(cpath) => unsafe { libc::access(cpath.as_ptr(), mode) == 0 },
        Err(_) => false,
    }
}

fn vu(value: impl Into<BigInt>) -> ServiceReply {
    ServiceReply::Value(RpcValue::VUnsigned(VUnsigned::new(value)))
}

fn boolean(value: bool) -> ServiceReply {
    ServiceReply::Value(RpcValue::Bool(value))
}

fn read_number(params: &mut PacketReader, ty: RpcType) -> Result<BigInt, ServiceError> {
    match read_param(params, &ty)? {
        RpcValue::VUnsigned(v) => Ok(v.value),
        RpcValue::VSigned(v) => Ok(v.value),
        _ => Err(ServiceError::Internal("number expected".to_string())),
    }
}

fn read_string(params: &mut PacketReader) -> Result<String, ServiceError> {
    match read_param(params, &RpcType::String)? {
        RpcValue::String(s) => Ok(s),
        _ => Err(ServiceError::Internal("string expected".to_string())),
    }
}

impl FtpBusService {
    pub fn new(root: impl AsRef<Path>) -> io::Result<Self> {
        let root = fs::canonicalize(root)?;
        Ok(Self {
            registry: Mutex::new(FileRegistry::open(&root)?),
        })
    }

    fn serve(&self, index: u8, params: &mut PacketReader) -> Result<ServiceReply, ServiceError> {
        let posix = ServiceError::Posix;
        let mut registry = self.registry.lock().unwrap();

        //every function gets the file number first
        let file_no = read_number(params, RpcType::VUnsigned)?;
        let path = registry.path_of(&file_no).map_err(posix)?;
        let file = registry.fs_path(&path);

        match index {
            //file attributes and filesystem discovery
            1 => Ok(boolean(file.exists())),

            2 => {
                if path == "/" {
                    return Err(posix(errno::ENOENT));
                }
                let parent = &path[..path.rfind('/').unwrap_or(0)];
                registry.number_of(parent).map(vu).map_err(posix)
            }

            3 => Ok(ServiceReply::Value(RpcValue::S8(if file.is_file() {
                1
            } else if file.is_dir() {
                2
            } else {
                127
            }))),

            4 => {
                let name = file
                    .file_name()
                    .map(|n| n.to_string_lossy().into_owned())
                    .unwrap_or_default();
                Ok(ServiceReply::Namespace(Box::new(UbString::new(&name))))
            }

            5 => {
                if file.is_dir() {
                    sorted_dir_names(&file)
                        .map(|names| vu(names.len()))
                        .map_err(|_| posix(errno::EIO))
                } else {
                    fs::metadata(&file)
                        .map(|meta| vu(meta.len()))
                        .map_err(|_| posix(errno::ENOENT))
                }
            }

            6 => {
                let names = sorted_dir_names(&file).map_err(|_| posix(errno::ENOTDIR))?;
                let mut numbers = Vec::new();
                for name in names.iter().filter(|name| *name != LIST_FILE_NAME) {
                    let child = child_path(&path, name).map_err(posix)?;
                    numbers.push(RpcValue::VUnsigned(VUnsigned::new(
                        registry.number_of(&child).map_err(posix)?,
                    )));
                }
                Ok(ServiceReply::Value(RpcValue::Array(numbers)))
            }

            7 => {
                let index = read_number(params, RpcType::VUnsigned)?;
                if !file.is_dir() {
                    return Err(posix(errno::ENOTDIR));
                }
                let names = sorted_dir_names(&file).map_err(|_| posix(errno::ENOTDIR))?;
                let name = index
                    .to_usize()
                    .and_then(|i| names.get(i))
                    .ok_or(posix(errno::ENOTDIR))?;
                let child = format!("{}/{name}", path.trim_end_matches('/'));
                registry.number_of(&child).map(vu).map_err(posix)
            }

            8 => Ok(boolean(can_access(&file, libc::R_OK))),
            9 => Ok(boolean(can_access(&file, libc::W_OK))),
            10 => Ok(vu(0)),

            11 => {
                let modified = fs::metadata(&file)
                    .and_then(|meta| meta.modified())
                    .ok()
                    .and_then(|time| time.duration_since(UNIX_EPOCH).ok())
                    .map_or(0, |d| d.as_millis() as u64);
                Ok(vu(modified))
            }

            //file read functions
            20 => {
                let from = read_number(params, RpcType::VUnsigned)?;
                let length = read_number(params, RpcType::VUnsigned)?;
                let length = length.to_usize().unwrap_or(usize::MAX);
                if length > MAX_FILE_PART {
                    return Err(posix(errno::EMSGSIZE));
                }

                let read = || -> io::Result<Vec<u8>> {
                    let mut input = File::open(&file)?;
                    input.seek(SeekFrom::Start(from.to_u64().unwrap_or(u64::MAX)))?;
                    let mut part = Vec::with_capacity(length);
                    input.take(length as u64).read_to_end(&mut part)?;
                    Ok(part)
                };
                read()
                    .map(|part| ServiceReply::Value(RpcValue::Bytes(part)))
                    .map_err(|_| posix(errno::EIO))
            }

            //file create, filesystem modify
            30 | 31 => {
                let name = read_string(params)?;
                if index == 30 && !file.is_dir() {
                    return Err(posix(errno::ENOTDIR));
                }

                let child = child_path(&path, &name).map_err(posix)?;
                let target = registry.fs_path(&child);
                if target.exists() {
                    return Err(posix(errno::EEXIST));
                }

                let created = if index == 30 {
                    fs::create_dir(&target)
                } else {
                    OpenOptions::new()
                        .write(true)
                        .create_new(true)
                        .open(&target)
                        .map(|_| ())
                };
                created.map_err(|_| posix(errno::EACCES))?;
                registry.number_of(&child).map(vu).map_err(posix)
            }

            32 => {
                let time = read_number(params, RpcType::VUnsigned)?;
                let set = || -> io::Result<()> {
                    let modified = UNIX_EPOCH + Duration::from_millis(time.to_u64().unwrap_or(0));
                    OpenOptions::new()
                        .write(true)
                        .open(&file)?
                        .set_modified(modified)
                };
                Ok(boolean(set().is_ok()))
            }

            33 => {
                let deleted = if file.is_dir() {
                    fs::remove_dir(&file)
                } else {
                    fs::remove_file(&file)
                };
                Ok(boolean(deleted.is_ok()))
            }

            //move and rename are not implemented in the protocol yet
            34 => {
                read_number(params, RpcType::VUnsigned)?;
                Ok(ServiceReply::Void)
            }
            35 => {
                read_string(params)?;
                Ok(ServiceReply::Void)
            }

            //modify file content
            40 => {
                let size = read_number(params, RpcType::VUnsigned)?;
                let truncate = || -> io::Result<u64> {
                    let output = OpenOptions::new().write(true).open(&file)?;
                    output.set_len(size.to_u64().unwrap_or(0))?;
                    Ok(output.metadata()?.len())
                };
                truncate().map(vu).map_err(|_| posix(errno::ENOENT))
            }

            41 => {
                let position = read_number(params, RpcType::VSigned)?;
                let data = match read_param(params, &RpcType::Bytes)? {
                    RpcValue::Bytes(data) => data,
                    _ => Vec::new(),
                };

                //negative position: append to the end
                let append = position < BigInt::from(0);
                let write = || -> io::Result<u64> {
                    let mut output = if append {
                        OpenOptions::new().append(true).open(&file)?
                    } else {
                        let mut output = OpenOptions::new().write(true).open(&file)?;
                        output.seek(SeekFrom::Start(position.to_u64().unwrap_or(0)))?;
                        output
                    };
                    output.write_all(&data)?;
                    output.flush()?;
                    Ok(output.metadata()?.len())
                };
                write()
                    .map(vu)
                    .map_err(|_| posix(if append { errno::EACCES } else { errno::ENOENT }))
            }

            _ => Err(ServiceError::Internal(format!("no function {index}"))),
        }
    }
}

const FUNCTIONS: [u8; 20] = [
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 20, 30, 31, 32, 33, 34, 35, 40, 41,
];

impl UbService for FtpBusService {
    fn call(&self, index: u8, params: &mut PacketReader) -> Option<ServiceCall> {
        if !FUNCTIONS.contains(&index) {
            return None;
        }
        Some(ServiceCall::posix(self.serve(index, params)))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn url_encoding_keeps_names_on_one_line_and_is_reversible() {
        assert_eq!(url_encode("/"), "%2F");
        assert_eq!(url_encode("/my file.txt"), "%2Fmy+file.txt");
        assert_eq!(url_encode("/a\nb"), "%2Fa%0Ab");
        for name in ["/", "/my file.txt", "/a\nb", "/árvíz+tűrő%20", "/100%"] {
            assert_eq!(url_decode(&url_encode(name)), name);
        }
    }

    #[test]
    fn file_names_can_not_leave_the_served_directory() {
        assert_eq!(child_path("/", "hello"), Ok("/hello".to_string()));
        assert_eq!(child_path("/dir", "hello"), Ok("/dir/hello".to_string()));
        assert_eq!(child_path("/", ".."), Err(errno::EACCES));
        assert_eq!(child_path("/", "a/../../b"), Err(errno::EACCES));
        assert_eq!(child_path("/", ".ubfs"), Err(errno::EACCES));
        assert_eq!(child_path("/", ""), Err(errno::EACCES));
    }
}
