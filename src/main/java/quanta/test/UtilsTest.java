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
		sig3();
	}

	public void sig3() throws Exception {
		String id = "126c4bc4a33b0b03cd947e164002066b70d97ce1c4b4f581c9f965c625069d41";
		String pubkey = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245";
		Long createdAt = 1681657931L;
		Integer kind = 1;

		ArrayList<ArrayList<String>> tags = new ArrayList<>();
		tags.add(new ArrayList<>(Arrays.asList("e", "3de94ee418667f1567a79eb4c76ff788e39a4caf1947d5ec75fcda15d8920b0b")));
		tags.add(new ArrayList<>(Arrays.asList("e", "2633adacdd31915719910b84c51b6a9b4bc8cdf165073bd8d5b2db8d36dc2fb3")));
		tags.add(new ArrayList<>(Arrays.asList("p", "d7f0e3917c466f1e2233e9624fbd6d4bd1392dbcfcaf3574f457569d496cb731")));
		tags.add(new ArrayList<>(Arrays.asList("p", "d7f0e3917c466f1e2233e9624fbd6d4bd1392dbcfcaf3574f457569d496cb731")));
		tags.add(new ArrayList<>(Arrays.asList("p", "43c27728028a8b631118e73a46b363b464fee19002da657cc0196e8260188a7e")));

		String content =
				"It runs fast on my ryzen 1800x (nixos) on 16 threads:\n\nsystem_info: n_threads = 16 / 16 | AVX = 1 | AVX2 = 1 | AVX512 = 0 | FMA = 1 | NEON = 0 | ARM_FMA = 0 | F16C = 1 | FP16_VA = 0 | WASM_SIMD = 0 | BLAS = 0 | SSE3 = 1 | VSX = 0 \n\nmy m1 shows:\n\nsystem_info: n_threads = 4 / 8 | AVX = 0 | AVX2 = 0 | AVX512 = 0 | FMA = 0 | NEON = 1 | ARM_FMA = 1 | F16C = 0 | FP16_VA = 1 | WASM_SIMD = 0 | BLAS = 1 | SSE3 = 0 | VSX = 0 \n\nusing about 1.5GB of ram?  ";
		String sig =
				"b9465f00cbc8c36c9140ad9360597b7bd16c9007bfb924576101d062f3cbaf10851950460bd876941e89d42c85cdfb0166908a9ce33cbfa12419a4ab430df30a";

		NostrCrypto.verifyEventProps(id, pubkey, createdAt, kind, content, tags, sig);
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
