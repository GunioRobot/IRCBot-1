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

import org.pircbotx.hooks.events.MessageEvent;
import java.util.Random;

public class FortuneHandler implements Runnable {
	// Variables
	private MessageEvent event;
	
	// Method that executes upon start of thread
	public void run() {
		event.respond(parseFortune(event.getMessage().substring(8)));
	}
	
	// Class constructor
	public FortuneHandler(MessageEvent event) {
		this.event = event;
	}
	
	// Method to parse and return a fortune
	private String parseFortune(String message) {
		// Split the message with the delimiter 'or'
		String[] splitMessage = message.split("or");
		// If the length of the new array is 1, we assume the user only wants a Yes/No response
		if(splitMessage.length == 1) {
			// Generate a random number and use it to return the fortune
			Random generator = new Random();
			if(generator.nextInt(2) == 1) return "Yes";
			else return "No";
		} else {
			// Generate a random number and use it to return a decision
			Random generator = new Random();
			return splitMessage[generator.nextInt(splitMessage.length)].replaceAll("^\\s+", "");
		}
	}
}
