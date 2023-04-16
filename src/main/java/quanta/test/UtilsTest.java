package quanta.test;

import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.service.nostr.NostrCrypto;

@Component("UtilsTest")
@Slf4j
public class UtilsTest extends ServiceBase implements TestIntf {

	@Override
	public void test() throws Exception {
		sig1();
		sig2();
	}

	public void sig2() throws Exception {
		String id = "1b71f57e1c3365f32b2a0abd8ded19f2a5e217a6eeaf3df11fa5c904bfb90dd6";
		String pubkey = "35d26e4690cbe1a898af61cc3515661eb5fa763b57bd0b42e45099c8b32fd50f";
		Long createdAt = 1681157953L;
		Integer kind = 1;

		ArrayList<ArrayList<String>> tags = new ArrayList<>();
		tags.add(new ArrayList<>(Arrays.asList("r", "https://btcplusplus.dev/talks#nostr")));

		String content =
				"I will be giving a talk on Nostr at the Bitcoin++ conference in Austin TX, end of this month, hoping to meet some other nostr people!  https://btcplusplus.dev/talks#nostr";
		String sig =
				"ec6e37c3d492654a4b8b5411b1872fc1dd7bbc3920d2c0818b177b42bd9429e206b878ad3b8fa0e72993b58182024d32a345ce487072de897bd0061d1c5e2189";

		NostrCrypto.verifyEventProps(id, pubkey, createdAt, kind, content, tags, sig);
	}


	public void sig1() throws Exception {
		String id = "b12e1d13848d47e28f912bb0a8b3d6b211d1af97f60e9e10b9cf5df9e5c731ed";
		String pubkey = "35d26e4690cbe1a898af61cc3515661eb5fa763b57bd0b42e45099c8b32fd50f";
		Long createdAt = 1677727939L;
		Integer kind = 0;
		String content =
				"{\"name\":\"scsibug\",\"picture\":\"https://s.gravatar.com/avatar/1a425d744df94198d68dfffbf7ae51cf\",\"about\":\"author of nostr-rs-relay, operates nostr-pub.wellorder.net\",\"nip05\":\"scsibug@wellorder.net\",\"lud06\":\"\",\"lud16\":\"outsideice77@walletofsatoshi.com\",\"username\":\"scsibug\",\"display_name\":\"\",\"displayName\":\"\",\"banner\":\"\",\"website\":\"\"}";
		String sig =
				"02f3e61059e99874cfd777f8cff44a2faa801429ab2cfd844adcc52e5c917ed886891d2cafab30fb4d38acfd20a399a2fd75e7472a4d13669735dc2cea84d6b6";

		NostrCrypto.verifyEventProps(id, pubkey, createdAt, kind, content, null, sig);
	}
}
