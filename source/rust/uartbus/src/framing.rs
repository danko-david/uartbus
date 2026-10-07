//! Escaped stream framing used between the PC and the bus gateway.
//!
//! A packet is terminated with `escape, !escape`; an escape byte inside the
//! packet is doubled.

pub fn frame_bytes(data: &[u8], escape: u8) -> Vec<u8> {
    let mut framed = Vec::with_capacity(data.len() + 4);

    for &byte in data {
        if byte == escape {
            framed.push(escape);
        }
        framed.push(byte);
    }

    framed.push(escape);
    framed.push(!escape);
    framed
}

/// Cuts packets out of a framed byte stream. Keeps its state between calls,
/// so a packet may arrive in any number of pieces.
pub struct FramedPacketCutter {
    escape: u8,
    may_cut: bool,
    packet: Vec<u8>,
}

impl FramedPacketCutter {
    pub fn new(escape: u8) -> Self {
        Self {
            escape,
            may_cut: false,
            packet: Vec::new(),
        }
    }

    /// Drops the bytes of the unfinished packet.
    pub fn clear(&mut self) {
        self.packet.clear();
    }

    pub fn feed_bytes(&mut self, bytes: &[u8], mut on_packet: impl FnMut(Vec<u8>)) {
        for &byte in bytes {
            if self.may_cut {
                if byte == self.escape {
                    self.packet.push(self.escape);
                } else {
                    on_packet(std::mem::take(&mut self.packet));
                }
                self.may_cut = false;
            } else if byte == self.escape {
                self.may_cut = true;
            } else {
                self.packet.push(byte);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn cut(cutter: &mut FramedPacketCutter, bytes: &[u8]) -> Vec<Vec<u8>> {
        let mut packets = Vec::new();
        cutter.feed_bytes(bytes, |p| packets.push(p));
        packets
    }

    #[test]
    fn framing_escapes_terminator_and_terminates_packet() {
        assert_eq!(
            frame_bytes(&[0xff, 0xff], 0xff),
            vec![0xff, 0xff, 0xff, 0xff, 0xff, 0x00]
        );
        assert_eq!(
            frame_bytes(&[0x14, 0x1e, 0x01, 0x00, 0x01], 0xff),
            vec![0x14, 0x1e, 0x01, 0x00, 0x01, 0xff, 0x00]
        );
    }

    #[test]
    fn terminator_is_the_inverse_of_the_escape_byte() {
        assert_eq!(frame_bytes(&[1, 0, 2], 0), vec![1, 0, 0, 2, 0, 0xff]);
    }

    #[test]
    fn cutter_recovers_packets() {
        let mut cutter = FramedPacketCutter::new(0xff);
        assert_eq!(
            cut(&mut cutter, &[0x14, 0x1e, 0x01, 0x00, 0x01, 0xff, 0x00]),
            vec![vec![0x14, 0x1e, 0x01, 0x00, 0x01]]
        );
    }

    #[test]
    fn cutter_recovers_a_packet_that_arrives_in_pieces() {
        let mut cutter = FramedPacketCutter::new(0xff);
        assert!(cut(&mut cutter, &[1, 2, 0xff]).is_empty());
        assert!(cut(&mut cutter, &[0xff, 3]).is_empty());
        assert!(cut(&mut cutter, &[0xff]).is_empty());
        assert_eq!(cut(&mut cutter, &[0x00, 9]), vec![vec![1, 2, 0xff, 3]]);
        assert_eq!(cut(&mut cutter, &[0xff, 0x00]), vec![vec![9]]);
    }

    #[test]
    fn cutter_recovers_what_frame_bytes_produced() {
        let packet = vec![0xff, 0, 0xff, 0xff, 7];
        let mut cutter = FramedPacketCutter::new(0xff);
        assert_eq!(cut(&mut cutter, &frame_bytes(&packet, 0xff)), vec![packet]);
    }
}
