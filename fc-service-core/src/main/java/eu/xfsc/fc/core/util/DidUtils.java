package eu.xfsc.fc.core.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class DidUtils {

	  public static URI resolveWebUri(URI uri) throws IOException {
		String[] uri_parts = uri.getSchemeSpecificPart().split(":");
		if (uri_parts.length >= 2 && "web".equals(uri_parts[0])) {
		  String url = "https://";
	      url += uri_parts[1];
		  if (uri_parts.length == 2) {
		    url += "/.well-known";
		  } else {
		    int idx;
		    try {
		      Integer.parseInt(uri_parts[2]);
		      url += ":" + uri_parts[2];
		      idx = 3;
		    } catch (NumberFormatException e) {
			  idx = 2;  
		    }
		    for (int i=idx; i < uri_parts.length; i++) {
			  url += "/" + uri_parts[i];
		    }
		  }
		  url += "/did.json";
		  if (uri.getFragment() != null) {
		    url += "#" + uri.getFragment();
		  }
		  try {
			return new URI(url);
		  } catch (URISyntaxException ex) {
			//log.warn("resolveWebUrl.error: {}, on url: {}", ex.getMessage(), url);
			throw new IOException(ex);
		  } 
	    }
	    return null;  
	  }  

	
}
