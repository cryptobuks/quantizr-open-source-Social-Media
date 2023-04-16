package quanta.test;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.service.nostr.NostrCrypto;

@Component("UtilsTest")
@Slf4j
public class UtilsTest extends ServiceBase implements TestIntf {

	@Override
	public void test() throws Exception {
		String id = "b12e1d13848d47e28f912bb0a8b3d6b211d1af97f60e9e10b9cf5df9e5c731ed";
		String pubkey = "35d26e4690cbe1a898af61cc3515661eb5fa763b57bd0b42e45099c8b32fd50f";
		Long createdAt = 1677727939L;
		Integer kind = 0;
		String content =
				"{\"name\":\"scsibug\",\"picture\":\"https://s.gravatar.com/avatar/1a425d744df94198d68dfffbf7ae51cf\",\"about\":\"author of nostr-rs-relay, operates nostr-pub.wellorder.net\",\"nip05\":\"scsibug@wellorder.net\",\"lud06\":\"\",\"lud16\":\"outsideice77@walletofsatoshi.com\",\"username\":\"scsibug\",\"display_name\":\"\",\"displayName\":\"\",\"banner\":\"\",\"website\":\"\"}";
		String sig =
				"02f3e61059e99874cfd777f8cff44a2faa801429ab2cfd844adcc52e5c917ed886891d2cafab30fb4d38acfd20a399a2fd75e7472a4d13669735dc2cea84d6b6";

		NostrCrypto.verifyEventProps(id, pubkey, createdAt, kind, content, sig);
	}
}
