//! Port of the Java UbFtpServerTest, extended: the file service through the (virtual) bus.

mod common;

use std::fs;
use std::sync::Arc;
use std::time::Duration;

use common::TempDir;
use uartbus::rpc::device::{errno, RpcError, UartBusDevice, UbFunction, UbRet, NS_APP};
use uartbus::rpc::ftp::FtpBusService;
use uartbus::rpc::service::UartbusServiceBridge;
use uartbus::rpc::{RpcType, RpcValue, VirtualUartBus};
use uartbus::{VSigned, VUnsigned};

struct Env {
    dir: TempDir,
    _bus: VirtualUartBus,
    _bridge: Arc<UartbusServiceBridge>,
    node: UartBusDevice,
}

/// A directory with `my_file` in it, served on the address 10 in the namespace 32.
fn setup() -> Env {
    let dir = TempDir::new("ubftp");
    fs::write(dir.path().join("my_file"), b"").unwrap();
    serve(dir)
}

fn serve(dir: TempDir) -> Env {
    let cb = VirtualUartBus::create_env().unwrap();
    let service_bus = cb.new_bus(10).unwrap();
    let bridge = UartbusServiceBridge::attach_service_to(
        Arc::new(FtpBusService::new(dir.path()).unwrap()),
        &service_bus,
        Some(10),
        &[NS_APP],
    );

    let mut node = cb.get_bus().device(10);
    node.timeout = Duration::from_millis(200);
    Env {
        dir,
        _bus: cb,
        _bridge: bridge,
        node,
    }
}

fn vu(n: u64) -> RpcValue {
    RpcValue::VUnsigned(VUnsigned::new(n))
}

fn number(value: RpcValue) -> u64 {
    match value {
        RpcValue::VUnsigned(v) => u64::try_from(v.value).unwrap(),
        other => panic!("number expected: {other:?}"),
    }
}

impl Env {
    /// Calls a function of the file service; all of them can answer with an error number.
    fn call(&self, index: u8, ret: RpcType, params: &[RpcValue]) -> Result<RpcValue, RpcError> {
        self.node
            .call(
                &[NS_APP],
                &UbFunction::new(index, UbRet::Value(ret)).posix(),
                params,
            )
            .map(Option::unwrap)
    }

    fn file_name(&self, file_no: u64) -> String {
        //getFileName selects a string namespace: the name is asked from that in parts
        let mut path = vec![NS_APP, 4];
        let mut asm = uartbus::PacketAssembler::new();
        uartbus::append_elements(&mut asm, &[vu(file_no)]).unwrap();
        path.extend(asm.done());

        let part = self
            .node
            .call(
                &path,
                &UbFunction::new(2, UbRet::Value(RpcType::Bytes)),
                &[vu(0), vu(256)],
            )
            .unwrap()
            .unwrap();
        match part {
            RpcValue::Bytes(bytes) => String::from_utf8(bytes).unwrap(),
            other => panic!("{other:?}"),
        }
    }

    fn list(&self, dir_no: u64) -> Vec<u64> {
        match self
            .call(
                6,
                RpcType::Array(Box::new(RpcType::VUnsigned)),
                &[vu(dir_no)],
            )
            .unwrap()
        {
            RpcValue::Array(items) => items.into_iter().map(number).collect(),
            other => panic!("{other:?}"),
        }
    }
}

#[test]
fn basics() {
    let env = setup();

    assert_eq!(
        env.call(1, RpcType::Bool, &[vu(0)]),
        Ok(RpcValue::Bool(true))
    );
    assert_eq!(
        env.file_name(0),
        env.dir.path().file_name().unwrap().to_str().unwrap()
    );

    //create new file, write string, ensure written to file, read content back
    let nf = number(
        env.call(
            31,
            RpcType::VUnsigned,
            &[vu(0), RpcValue::String("hello".into())],
        )
        .unwrap(),
    );

    let size = env
        .call(
            41,
            RpcType::VUnsigned,
            &[
                vu(nf),
                RpcValue::VSigned(VSigned::new(-1)),
                RpcValue::Bytes(b"world".to_vec()),
            ],
        )
        .unwrap();
    assert_eq!(number(size), 5);

    let hello = env.dir.path().join("hello");
    assert!(hello.exists());
    assert_eq!(fs::read(&hello).unwrap(), b"world");

    assert_eq!(
        env.call(20, RpcType::Bytes, &[vu(nf), vu(0), vu(1024)]),
        Ok(RpcValue::Bytes(b"world".to_vec()))
    );
}

#[test]
fn files_of_a_directory_are_listed_without_the_registry_file() {
    let env = setup();
    let files = env.list(0);
    assert_eq!(files.len(), 1);
    assert_eq!(env.file_name(files[0]), "my_file");

    //type: 1 regular file, 2 directory
    assert_eq!(
        env.call(3, RpcType::S8, &[vu(files[0])]),
        Ok(RpcValue::S8(1))
    );
    assert_eq!(env.call(3, RpcType::S8, &[vu(0)]), Ok(RpcValue::S8(2)));
}

#[test]
fn directory_can_be_created_and_its_files_know_their_parent() {
    let env = setup();
    let dir = number(
        env.call(
            30,
            RpcType::VUnsigned,
            &[vu(0), RpcValue::String("sub".into())],
        )
        .unwrap(),
    );
    assert!(env.dir.path().join("sub").is_dir());

    let file = number(
        env.call(
            31,
            RpcType::VUnsigned,
            &[vu(dir), RpcValue::String("inner".into())],
        )
        .unwrap(),
    );
    assert!(env.dir.path().join("sub/inner").is_file());

    assert_eq!(env.list(dir), vec![file]);
    assert_eq!(
        number(env.call(2, RpcType::VUnsigned, &[vu(file)]).unwrap()),
        dir
    );
    assert_eq!(
        number(env.call(2, RpcType::VUnsigned, &[vu(dir)]).unwrap()),
        0
    );
}

#[test]
fn file_content_is_read_in_parts_and_written_at_a_position() {
    let env = setup();
    fs::write(env.dir.path().join("my_file"), b"0123456789").unwrap();
    let file = env.list(0)[0];

    assert_eq!(
        number(env.call(5, RpcType::VUnsigned, &[vu(file)]).unwrap()),
        10
    );
    assert_eq!(
        env.call(20, RpcType::Bytes, &[vu(file), vu(3), vu(4)]),
        Ok(RpcValue::Bytes(b"3456".to_vec()))
    );
    //reading over the end gives what there is
    assert_eq!(
        env.call(20, RpcType::Bytes, &[vu(file), vu(8), vu(100)]),
        Ok(RpcValue::Bytes(b"89".to_vec()))
    );

    //write at position 2
    env.call(
        41,
        RpcType::VUnsigned,
        &[
            vu(file),
            RpcValue::VSigned(VSigned::new(2)),
            RpcValue::Bytes(b"ab".to_vec()),
        ],
    )
    .unwrap();
    assert_eq!(
        fs::read(env.dir.path().join("my_file")).unwrap(),
        b"01ab456789"
    );

    //truncate
    assert_eq!(
        number(
            env.call(40, RpcType::VUnsigned, &[vu(file), vu(4)])
                .unwrap()
        ),
        4
    );
    assert_eq!(fs::read(env.dir.path().join("my_file")).unwrap(), b"01ab");
}

#[test]
fn errors_are_answered_with_error_numbers() {
    let env = setup();

    //no such file number
    assert_eq!(
        env.call(1, RpcType::Bool, &[vu(999)]),
        Err(RpcError::Posix(errno::ENOENT))
    );
    //creating what exists
    assert_eq!(
        env.call(
            31,
            RpcType::VUnsigned,
            &[vu(0), RpcValue::String("my_file".into())]
        ),
        Err(RpcError::Posix(errno::EEXIST))
    );
    //too large part asked
    assert_eq!(
        env.call(20, RpcType::Bytes, &[vu(0), vu(0), vu(5000)]),
        Err(RpcError::Posix(errno::EMSGSIZE))
    );
    //mkdir in something that is not a directory
    let file = env.list(0)[0];
    assert_eq!(
        env.call(
            30,
            RpcType::VUnsigned,
            &[vu(file), RpcValue::String("x".into())]
        ),
        Err(RpcError::Posix(errno::ENOTDIR))
    );
}

#[test]
fn served_directory_can_not_be_left_through_a_file_name() {
    let env = setup();
    for name in ["../escaped", "a/b", ".."] {
        assert_eq!(
            env.call(
                31,
                RpcType::VUnsigned,
                &[vu(0), RpcValue::String(name.into())]
            ),
            Err(RpcError::Posix(errno::EACCES)),
            "{name}"
        );
    }
    assert!(!env.dir.path().parent().unwrap().join("escaped").exists());
}

#[test]
fn file_can_be_deleted() {
    let env = setup();
    let file = env.list(0)[0];
    assert_eq!(
        env.call(33, RpcType::Bool, &[vu(file)]),
        Ok(RpcValue::Bool(true))
    );
    assert!(!env.dir.path().join("my_file").exists());
    assert_eq!(
        env.call(1, RpcType::Bool, &[vu(file)]),
        Ok(RpcValue::Bool(false))
    );
    assert!(env.list(0).is_empty());
}

#[test]
fn file_numbers_are_the_same_after_restart() {
    let env = setup();
    let created = number(
        env.call(
            31,
            RpcType::VUnsigned,
            &[vu(0), RpcValue::String("kept file".into())],
        )
        .unwrap(),
    );
    let my_file = env.list(0).into_iter().find(|n| *n != created).unwrap();

    //stop serving, then serve the same directory again
    let Env { dir, .. } = env;
    let env = serve(dir);

    assert_eq!(env.file_name(created), "kept file");
    assert_eq!(env.file_name(my_file), "my_file");

    let registry = fs::read_to_string(env.dir.path().join(".ubfs")).unwrap();
    assert_eq!(registry, "%2F\n%2Fmy_file\n%2Fkept+file\n");
}
