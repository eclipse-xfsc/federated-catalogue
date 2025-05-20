package eu.xfsc.fc.core.exception;

public class DidException extends ServiceException {

	public DidException(String message) {
		super(message);
	}

	public DidException(Throwable cause) {
		super(cause);
	}

	public DidException(String message, Throwable cause) {
		super(message, cause);
	}

}
