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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VotekickHandler implements Runnable {
	// Variables
	private MessageEvent event;
	
	// We need these variables to be accessible from other threads, so we make it static and volatile
	private static volatile String votekickUser = "";
	private static volatile int requiredVotes = -1;
	private static volatile List<String> votedUsers = new ArrayList<String>();
	
	// Method that executes upon start of thread
	public void run() {
		// There is no votekick in progress
		if(votekickUser.equals("")) {
			// Set the current votekick user
			synchronized(votekickUser) {
				votekickUser = event.getMessage().substring(10).replaceAll("^\\s+", "").replaceAll("\\s+$", "");
			}
			// Determine the number of required votes to pass
			requiredVotes = (int)(event.getChannel().getUsers().size() * 0.4);
			// Ensure the user we wish to kick exists - if not, fail and reset for the next vote
			if(event.getBot().userExists(votekickUser) == false) {
				event.respond("Cannot votekick user - user doesn't exist!");
				resetKick();
				return;
			}
			// Add the vote starter as a voted user
			votedUsers.add(event.getUser().getNick());
			// Announce the votekick
			event.getBot().sendMessage(event.getChannel(), event.getUser().getNick() + " has voted to kick " + votekickUser + "! Type !votekick " + votekickUser + " to cast a vote. (" + requiredVotes + " needed)");
			// Sleep for a certain period of time.
			try {
				Thread.sleep(30000);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
			// See if the vote has reached a conclusion. If not, fail and reset the vote.
			if(!votekickUser.equals("")) {
				event.getBot().sendMessage(event.getChannel(), "The vote to kick " + votekickUser + " has failed! (" + requiredVotes + " more needed)");
				resetKick();
			}
		}
		// There is a vote in progress and the user has voted to kick
		else if(votekickUser.equals(event.getMessage().substring(10).replaceAll("^\\s+", "").replaceAll("\\s+$", ""))) {
			// Ensure the user isn't trying to vote more than once
			if(hasVoted(event.getUser().getNick())) {
				event.respond("You cannot vote more than once!");
				return;
			}
			// One less required vote to pass
			requiredVotes--;
			// Announce the vote to kick
			event.getBot().sendMessage(event.getChannel(), event.getUser().getNick() + " has voted to kick " + votekickUser + "! (" + requiredVotes + " needed)");
			// If we don't need any more votes to pass, kick the user and reset the system
			if(requiredVotes == 0) {
				event.getBot().sendMessage(event.getChannel(), "Vote succeeded - kicking " + votekickUser + "!");
				event.getBot().kick(event.getChannel(), event.getBot().getUser(votekickUser));
				resetKick();
			}
		}
		// A votekick is in progress and someone is trying to start a new one
		else {
			event.respond("You cannot vote to kick another user while a votekick is currently in progress.");
		}
	}
	
	// Class constructor
	public VotekickHandler(MessageEvent event) {
		this.event = event;
	}
	
	// Method to reset the votekick system when a vote has finished
	private void resetKick() {
		synchronized(votekickUser) {
			votekickUser = "";
		}
		synchronized(votedUsers) {
			votedUsers = new ArrayList<String>();
		}
		requiredVotes = -1;
	}
	
	// Method to check if a user has voted in the votekick
	private boolean hasVoted(String nick) {
		if(votedUsers.contains(nick)) return true;
		else return false;
	}

}
