package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.Comparator;

/**
 * POJO Class for holding the validators, that signed the Self-Description.
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

    public static class ExpirationComparator implements Comparator<Validator> {

		@Override
		public int compare(Validator v1, Validator v2) {
			if (v1.getExpirationDate().isBefore(v2.getExpirationDate())) return -1;
			if (v1.getExpirationDate().isAfter(v2.getExpirationDate())) return 1;
			return 0;
		}
    }
}


