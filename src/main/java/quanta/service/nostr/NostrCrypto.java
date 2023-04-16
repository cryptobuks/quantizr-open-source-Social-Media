package quanta.service.nostr;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import quanta.model.client.NostrEvent;

/*
 * The code for verifying signatures and the Pair/Point classes came directly from here:
 * 
 * https://github.com/tcheeric/nostr-java
 */

@Slf4j
public class NostrCrypto {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static boolean verifyEvent(NostrEvent event) {
        return verifyEventProps(event.getId(), event.getPk(), event.getTimestamp(), event.getKind(), event.getContent(),
                event.getSig());
    }

    public static boolean verifyEventProps(String id, String pubKey, Long createdAt, Integer kind, String content, String sig) {
        StringBuilder serialized = new StringBuilder();
        serialized.append("[");
        serialized.append("0").append(",\"");
        serialized.append(pubKey).append("\",");
        serialized.append(createdAt).append(",");
        serialized.append(kind).append(",");
        // todo-0: some example code I'm seeing looks like tags are recursive? How to include tags here?
        serialized.append("[]"); // new TagListMarshaller(tags, null).marshall());
        serialized.append(",\"");
        serialized.append(content.replace("\"", "\\\""));
        serialized.append("\"]");

        byte[] serBytes = serialized.toString().getBytes(StandardCharsets.UTF_8);
        byte[] sha256 = sha256(serBytes);
        String genId = bytesToHex(sha256);
        if (!id.equals(genId)) {
            log.debug("ID does not match object.");
            return false;
        }

        boolean verified = verify(sha256, hexToBytes(pubKey), hexToBytes(sig));
        log.debug("Verified = " + verified);
        return verified;
    }

    public static String bytesToHex(byte[] b) {
        char[] hexChars = new char[b.length * 2];
        for (int j = 0; j < b.length; j++) {
            int v = b[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars).toLowerCase();
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] buf = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            buf[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return buf;
    }

    public static String sha256(String val) {
        return bytesToHex(sha256(val.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] sha256(byte[] b) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(b);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(byte[] msg, byte[] pubkey, byte[] sig) {
        try {
            if (msg.length != 32) {
                log.debug("The message must be a 32-byte array.");
                return false;
            }
            if (pubkey.length != 32) {
                log.debug("The public key must be a 32-byte array.");
                return false;
            }
            if (sig.length != 64) {
                log.debug("The signature must be a 64-byte array.");
                return false;
            }

            Point P = Point.liftX(pubkey);
            if (P == null) {
                return false;
            }
            BigInteger r = bigIntFromBytes(Arrays.copyOfRange(sig, 0, 32));
            BigInteger s = bigIntFromBytes(Arrays.copyOfRange(sig, 32, 64));
            if (r.compareTo(Point.getp()) >= 0 || s.compareTo(Point.getn()) >= 0) {
                return false;
            }
            int len = 32 + pubkey.length + msg.length;
            byte[] buf = new byte[len];
            System.arraycopy(sig, 0, buf, 0, 32);
            System.arraycopy(pubkey, 0, buf, 32, pubkey.length);
            System.arraycopy(msg, 0, buf, 32 + pubkey.length, msg.length);
            BigInteger e = bigIntFromBytes(Point.taggedHash("BIP0340/challenge", buf)).mod(Point.getn());
            Point R = Point.add(Point.mul(Point.getG(), s), Point.mul(P, Point.getn().subtract(e)));
            return R != null && R.hasEvenY() && R.getX().compareTo(r) == 0;
        } catch (Exception e) {
            // ignore this;
            return false;
        }
    }

    public static BigInteger bigIntFromBytes(byte[] b) {
        return new BigInteger(1, b);
    }
}
