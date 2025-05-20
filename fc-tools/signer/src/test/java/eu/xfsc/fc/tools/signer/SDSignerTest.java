package eu.xfsc.fc.tools.signer;

import org.junit.jupiter.api.Test;

public class SDSignerTest {
	
	
	@Test
	public void testVPSignature() throws Exception {
		
//		SDSigner.main(new String[] {"sd=legalPerson_one_VC.jsonld", "ssd=legalPerson_one_VC_signed.jsonld"});
//		SDSigner.main(new String[] {"sd=participant_v1.jsonld", "ssd=participant_v1_signed.jsonld", "puk=src/main/resources/cert.ss.pem", "m=did:web:compliance.lab.gaia-x.eu:v1"});
		SDSigner.main(new String[] {"sd=legalPerson_two_VC.jsonld", "puk=src/main/resources/cert.ss.pem", "m=did:web:compliance.lab.gaia-x.eu"});
	}

	@Test 
	//fails from cmd line, for some reason.. java.lang.NullPointerException: Cannot invoke "foundation.identity.jsonld.JsonLDObject.getDocumentLoader()" because "jsonLdObject" is null; works perfectly from IDE
	public void testVCSignature() throws Exception {
		
//		SDSigner.main(new String[] {"sd=tl_vc_3s.jsonld", "ssd=tl_vc_signed.jsonld", "puk=src/main/resources/rsa2048.pub.pem", "prk=src/main/resources/rsa2048.sign.pem", 
//			"m=did:jwk:eyJlIjoiQVFBQiIsImtpZCI6InNpZ25SU0EyMDQ4Iiwia3R5IjoiUlNBIiwibiI6IjhDUl8ySUJuSGlDcjVIY0kxcWh1dXpiRmdMRjFlSVI3dGM1NTF2WlN2ek1rTEtIVEpxeHVZR1BlVXp2U29ndWctTDJRQ2tpVWdmSjFZUzRFUmhrRFM2cVBJbFdMM1BGbkc4VjZQVWxoODZjTjgxWkhZdkhoUFYwUDdKT25UeGVKcTV1a1dFT0ctMUxrTmk5eGVnelRoMEVwV0w4NU5aUmJOYWJ0R0VqTUhqY04yak9NUldSYkE4OVdENVhOTExwdWUyNVlxbHcxOGZsbXNNRmh5dXk2YlVmanR2aXJ5SThwb0RLOVpXcGtPUjN6ejlNN0hpQXVyZ2NLSks5ODlEX0UzZ3d3S1FhQ21CbEJ4X3hESlJoVFB1bnJZUFBySHlpMExmaG5CeWgzcEJRMWhyelFLblZyX18zYkVwcE5abmRFLVhlWTY0VzNNaGUwa1RrREp2VXJndyJ9#0"});
		SDSigner.main(new String[] {"sd=participant_v1.jsonld", "ssd=participant_jwk_signed.jsonld", "prk=src/main/resources/rsa2048.sign.pem", 
		"m=did:jwk:eyJlIjoiQVFBQiIsImtpZCI6InNpZ25SU0EyMDQ4Iiwia3R5IjoiUlNBIiwibiI6IjhDUl8ySUJuSGlDcjVIY0kxcWh1dXpiRmdMRjFlSVI3dGM1NTF2WlN2ek1rTEtIVEpxeHVZR1BlVXp2U29ndWctTDJRQ2tpVWdmSjFZUzRFUmhrRFM2cVBJbFdMM1BGbkc4VjZQVWxoODZjTjgxWkhZdkhoUFYwUDdKT25UeGVKcTV1a1dFT0ctMUxrTmk5eGVnelRoMEVwV0w4NU5aUmJOYWJ0R0VqTUhqY04yak9NUldSYkE4OVdENVhOTExwdWUyNVlxbHcxOGZsbXNNRmh5dXk2YlVmanR2aXJ5SThwb0RLOVpXcGtPUjN6ejlNN0hpQXVyZ2NLSks5ODlEX0UzZ3d3S1FhQ21CbEJ4X3hESlJoVFB1bnJZUFBySHlpMExmaG5CeWgzcEJRMWhyelFLblZyX18zYkVwcE5abmRFLVhlWTY0VzNNaGUwa1RrREp2VXJndyJ9#0"});
	}

	//@Test
	public void testW3CVPSignature() throws Exception {
		
		SDSigner.main(new String[] {"sd=legalPerson_one_VC.jsonld", "ssd=legalPerson_W3C_signed.jsonld", "prk=src/main/resources/w3c_private_key.json", "m=https://example.com/issuer/123"});
	}

	//@Test
	public void testW3CVCSignature() throws Exception {
		
		SDSigner.main(new String[] {"sd=w3c_vc_sample.jsonld", "prk=src/main/resources/w3c_private_key.json", "m=https://example.com/issuer/123#ovsDKYBjFemIy8DVhc-w2LSi8CvXMw2AYDzHj04yxkc"});
	}
}


