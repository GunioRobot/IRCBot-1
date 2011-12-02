/*
 * This file is part of IRCBot.
 * Copyright (c) 2011 Ryan Morrison
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions, and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions, and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of the author of this software nor the name of
 *  contributors to this software may be used to endorse or promote products
 *  derived from this software without specific prior written consent.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */

package us.rddt.IRCBot;

import org.apache.commons.lang3.StringEscapeUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pircbotx.hooks.events.MessageEvent;

public class URLGrabber implements Runnable {
	// Variables
	private MessageEvent event = null;
	private URL url = null;
	
	// Regex pattern to match the HTML title tag to extract from the URL
	private static final Pattern TITLE_TAG = Pattern.compile("\\<title>(.*)\\</title>", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
	
	// Method that executes upon start of thread
	public void run() {
		try {
			event.getBot().sendMessage(event.getChannel(), ("[URL by '" + event.getUser().getNick() + "'] " + getPageTitle(url)));
		} catch (Exception ex) {
			// TODO: Better exception handling
			ex.printStackTrace();
			event.getBot().sendMessage(event.getChannel(), ("[URL by '" + event.getUser().getNick() + "'] An error occurred while retrieving this URL. (" + ex.getMessage() + ")"));
		}
	}
	
	// Class constructor
	public URLGrabber(MessageEvent event, URL url) {
		this.event = event;
		this.url = url;
	}
	
	// Main worker function to download and extract the title from a URL
	// TODO: Better exception handling
	public String getPageTitle(URL url) throws Exception {
		// No need to check validity of the URL - it's already been proven valid at this point
		URLConnection conn = url.openConnection();
		// Set a proper user agent, some sites return HTTP 409 without it
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; rv:6.0) Gecko/20110814 Firefox/6.0");
		// Get the Content-Type property from the HTTP headers so we can parse accordingly
		ContentType contentType = getContentTypeHeader(conn);
		// If the document isn't HTML, return the Content-Type and Content-Length instead
		if(!contentType.contentType.equals("text/html")) {
			return "Type: " + contentType.contentType + ", length: " + humanReadableByteCount(getContentLengthHeader(conn), true);
		}
		else {
			// Get the character set or use the default accordingly
			Charset charset = getCharset(contentType);
			if(charset == null) charset = Charset.defaultCharset();
			// Create and prepare our streams for reading from the web server
			InputStream in = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset));
			// More variables
			int n = 0, totalRead = 0;
			char[] buf = new char[1024];
			StringBuilder content = new StringBuilder();

			// Read the page from the web server into the buffer up to 8192 bytes and create a string from it
			// The first 8192 bytes should be enough to fetch the title, while saving us time and bandwidth
			while(totalRead < 8192 && (n = reader.read(buf, 0, buf.length)) != -1) {
				content.append(buf, 0, n);
				totalRead += n;
			}
			// Close the BufferedReader
			reader.close();

			// Use the Regex defined earlier to match and extract the title tag
			// If the title tag can't be matched, it likely wasn't part of the first 8192 bytes
			Matcher matcher = TITLE_TAG.matcher(content);
			if (matcher.find()) {
				// Properly escape any HTML entities present in the title
				new StringEscapeUtils();
				return StringEscapeUtils.unescapeHtml4(matcher.group(1).replaceAll("[\\s\\<>]+", " ").trim());
			}
			else
				return "Title not found or not within first 8192 bytes of page, aborting.";
		}
	}
	
	// Method to extract the Content-Type property from the HTTP response
	private static ContentType getContentTypeHeader(URLConnection conn) {
		// Variables
        int i = 0;
        boolean moreHeaders = true;
        // Loop through the headers until we find the Content-Type property
        // If we find it, break out, otherwise continue reading
        do {
            String headerName = conn.getHeaderFieldKey(i);
            String headerValue = conn.getHeaderField(i);
            if (headerName != null && headerName.equals("Content-Type"))
                return new ContentType(headerValue);
            i++;
            moreHeaders = headerName != null || headerValue != null;
        }
        while (moreHeaders);
        // If we reach this point we couldn't find the headers we need, so return null
        return null;
    }
	
	// Method to extract the Content-Length property from the HTTP response
	private static int getContentLengthHeader(URLConnection conn) {
		// Variables
        int i = 0;
        boolean moreHeaders = true;
     // Loop through the headers until we find the Content-Length property
        // If we find it, break out, otherwise continue reading
        do {
            String headerName = conn.getHeaderFieldKey(i);
            String headerValue = conn.getHeaderField(i);
            if (headerName != null && headerName.equals("Content-Length"))
                return Integer.parseInt(headerValue);
            i++;
            moreHeaders = headerName != null || headerValue != null;
        }
        while (moreHeaders);
        // If we reach this point we couldn't find the headers we need, so return 0
        return 0;
    }
	
	// Method to extract the character set from the Content-Type property
	private static Charset getCharset(ContentType contentType) {
		// Extract the character set from the character set or return null upon failure
        if (contentType != null && contentType.charsetName != null && Charset.isSupported(contentType.charsetName))
            return Charset.forName(contentType.charsetName);
        else
            return null;
    }
	
	// Class for handling the Content-Type property
    private static final class ContentType {
    	// Regex pattern to match the character set from the Content-Type
        private static final Pattern CHARSET_HEADER = Pattern.compile("charset=([-_a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
 
        // Variables
        private String contentType;
        private String charsetName;
        
        // Constructor for the ContentType class
        private ContentType(String headerValue) {
        	// Throw an exception should the passed parameter be null
            if (headerValue == null)
                throw new IllegalArgumentException("ContentType must be constructed with a not-null headerValue");
            // Locate the index of the semicolon in the header and use the regex above to match and extract the character set
            // If a semicolon doesn't exist then the character set was never provided, so set the Content-Type appropriately
            int n = headerValue.indexOf(";");
            if (n != -1) {
                contentType = headerValue.substring(0, n);
                Matcher matcher = CHARSET_HEADER.matcher(headerValue);
                if (matcher.find())
                    charsetName = matcher.group(1);
            }
            else
                contentType = headerValue;
        }
    }
    
    // Convert a data measurement value to a more human-readable format
    public static String humanReadableByteCount(long bytes, boolean si) {
    	// Variable for the unit of measurement used
        int unit = si ? 1000 : 1024;
        // If our value is less than a kilobyte than just return the value untouched in bytes
        if (bytes < unit) return bytes + " B";
        // Otherwise, properly convert to the appropriate human-readable value and return it
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
