package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.Comparator;

/**
 * POJO Class for holding the validators, that signed the credential.
 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.EqualsAndHashCode
@lombok.ToString
public class Validator {

	private String didURI;
    private String publicKey;
    private Instant expirationDate;

    /**
     * Compares validators by expiration date, treating null as "no expiration" (sorts last).
     */
    public static class ExpirationComparator implements Comparator<Validator> {

		@Override
		public int compare(Validator v1, Validator v2) {
			Instant e1 = v1.getExpirationDate();
			Instant e2 = v2.getExpirationDate();
			if (e1 == null && e2 == null) return 0;
			if (e1 == null) return 1;
			if (e2 == null) return -1;
			return e1.compareTo(e2);
		}
    }
}


