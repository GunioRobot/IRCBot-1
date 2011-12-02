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
import java.sql.*;
import java.util.Date;

public class ShoutHandler implements Runnable {
	// Variables
	private MessageEvent event = null;
	private Connection connect = null;
	private Statement statement = null;
	private PreparedStatement preparedStatement = null;
	private ResultSet resultSet = null;
	private String randomQuote = null;
	private boolean isRandomShout = false;
	
	// We need this variable to be accessible from other threads, so we make it static and volatile
	private static volatile String lastQuote = "";
	
	// Method that executes upon start of thread
	public void run() {
		try {
			// Connect to the database
			connect();
			// If the message passed is NOT a !who command
			if(isRandomShout) {
				// Get a random quote from the database (if possible). Send it to the channel.
				// If the quote does not exist in the database, add it!
				if((randomQuote = getRandomQuote()) != null) {
					event.getBot().sendMessage(event.getChannel(), randomQuote);
				}
				if(!doesQuoteExist()) addNewQuote();
			} else {
				// We're dealing with a !who command - respond to the user with the information about the quote.
				event.respond(getQuoteInfo(event.getMessage().substring(5)));
			}
			// Disconnect from the database
			disconnect();
		} catch (Exception ex) {
			// TODO: Better exception handling
			ex.printStackTrace();
		}
	}
	
	// Constructor for the class
	public ShoutHandler(MessageEvent event) {
		this.event = event;
	}
	
	// Overloadable constructor, used when a shout needs to be processed
	public ShoutHandler(MessageEvent event, boolean isRandomShout) {
		this.event = event;
		this.isRandomShout = isRandomShout;
	}
	
	// Method to connect to the database
	private void connect() throws SQLException, ClassNotFoundException {
		Class.forName("com.mysql.jdbc.Driver");
		connect = DriverManager.getConnection("jdbc:mysql://localhost/irc_shouts?user=ircbot&password=milkircbot");
		statement = connect.createStatement();
	}
	
	// Method to clean up/disconnect connections to the database
	private void disconnect() throws SQLException {
		if(resultSet != null) resultSet.close();
		if(statement != null) statement.close();
		if(connect != null) connect.close();
	}
	
	// Method to retrieve a random quote from the database
	private String getRandomQuote() throws SQLException {
		// We use prepared statements to sanitize input from the user
		// Specifying the channel allows different channels to have their own list of quotes available
		preparedStatement = connect.prepareStatement("SELECT * FROM Quotes WHERE Channel = ? ORDER BY RAND() LIMIT 1");
		preparedStatement.setString(1, event.getChannel().getName());
		// Execute our query against the database
		resultSet = preparedStatement.executeQuery();
		if(resultSet.next()) {
			// Strings are not thread-safe, so ensure synchronization on our shared string to prevent thread
			// interference and memory consistency issues
			synchronized(lastQuote) {
				lastQuote = resultSet.getString("Quote");
			}
			// Return the random quote
			return resultSet.getString("Quote");
		} else {
			// The database query returned nothing, so return null
			return null;
		}
	}
	
	// Method to check if a quote exists
	private boolean doesQuoteExist() throws SQLException {
		// Again, prepared statements to sanitize input
		preparedStatement = connect.prepareStatement("SELECT * FROM Quotes WHERE Quote = ? AND Channel = ?");
		preparedStatement.setString(1, event.getMessage());
		preparedStatement.setString(2, event.getChannel().getName());
		resultSet = preparedStatement.executeQuery();
		if(resultSet.next()) {
			return true;
		} else {
			return false;
		}
	}
	
	// Method to handle !who requests from users, returns submitter and timestamp
	// TODO: We can do one less hit on the database if we store everything from the last quote in the JVM
	private String getQuoteInfo(String quote) throws SQLException {
		// Variable to track whether the user requested the last quote from the bot
		boolean isLast = false;
		if(quote.equals("last")) {
			// On startup there is no previous quote, so return as such if a user attempts a !who last
			if(lastQuote == null || lastQuote == "") return "No previous quote.";
			quote = lastQuote;
			isLast = true;
		}
		// You should know why by now.
		preparedStatement = connect.prepareStatement("SELECT * FROM Quotes WHERE Quote = ? AND Channel = ?");
		preparedStatement.setString(1, quote);
		preparedStatement.setString(2, event.getChannel().getName());
		resultSet = preparedStatement.executeQuery();
		if(resultSet.next()) {
			// Tease the user if it's their own quote
			if(resultSet.getString("Nick").equals(event.getUser().getNick())) return "don't you remember? YOU submitted this! Put down the bong!";
			if(isLast) {
				// Provide context if the !who last command was used, but trim it if the quote is longer than 10 characters (use 60% of the quote instead)
				if(quote.length() < 11) {
					return resultSet.getString("Nick") + " shouted \"" + quote + "\" " + toReadableTime((Date)resultSet.getTimestamp("Date")) + ".";
				} else {
					return resultSet.getString("Nick") + " shouted \"" + quote.substring(0, (int)(quote.length() * 0.6)) + "...\" " + toReadableTime((Date)resultSet.getTimestamp("Date")) + ".";
				}
			}
			return resultSet.getString("Nick") + " shouted this " + toReadableTime((Date)resultSet.getTimestamp("Date")) + ".";
		} else {
			return "Quote not found.";
		}
	}
	
	// Method to add a new quote to the database.
	private void addNewQuote() throws SQLException {
		java.util.Date dt = new java.util.Date();
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		preparedStatement = connect.prepareStatement("INSERT INTO Quotes(Nick, Date, Channel, Quote) VALUES (?, ?, ?, ?)");
		preparedStatement.setString(1, event.getUser().getNick());
		preparedStatement.setString(2, sdf.format(dt));
		preparedStatement.setString(3, event.getChannel().getName());
		preparedStatement.setString(4, event.getMessage());
		preparedStatement.executeUpdate();
	}
	
	// Method to convert a date into a more readable time format.
	// TODO: Consider using an imprecise 'about X hours ago' format.
	private String toReadableTime(Date date) {
		String readableTime = "";
		// Calculate the difference in seconds between the quote's submission and now
		long diffInSeconds = (new Date().getTime() - date.getTime()) / 1000;

		// Calculate the appropriate day/hour/minute/seconds ago values and insert them into a long array
	    long diff[] = new long[] { 0, 0, 0, 0 };
	    diff[3] = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
	    diff[2] = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60 : diffInSeconds;
	    diff[1] = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24 : diffInSeconds;
	    diff[0] = (diffInSeconds = (diffInSeconds / 24));
	    
	    // Build the readable format string, ignoring values should they equal zero
	    if(diff[0] != 0) readableTime += (String.format("%d day%s, ", diff[0], diff[0] > 1 ? "s" : ""));
	    if(diff[1] != 0) readableTime += (String.format("%d hour%s, ", diff[1], diff[1] > 1 ? "s" : ""));
	    if(diff[2] != 0) readableTime += (String.format("%d minute%s, ", diff[2], diff[2] > 1 ? "s" : ""));
	    readableTime += (String.format("%d second%s ago", diff[3], diff[3] > 1 ? "s" : ""));

	    // Return our human-readable date time string
	    return readableTime;
	}
}
