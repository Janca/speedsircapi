package com.speed.irc.framework;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import com.speed.irc.connection.Server;
import com.speed.irc.event.ApiEvent;
import com.speed.irc.event.ApiListener;
import com.speed.irc.event.ExceptionEvent;
import com.speed.irc.event.IRCEventListener;
import com.speed.irc.types.Channel;

/**
 * The abstract class for making robots. To create a robot, you can extend this
 * class.
 * <p/>
 * This file is part of Speed's IRC API.
 * <p/>
 * Speed's IRC API is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * <p/>
 * Speed's IRC API is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public License
 * along with Speed's IRC API. If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Shivam Mistry
 */
public abstract class Bot implements ApiListener {

	private Server server;
	protected final int port;
	protected Logger logger = Logger.getLogger(Bot.class.getName());
	protected int modes;

	/**
	 * Gets the port the bot is connected to
	 * 
	 * @return the port the bot is connected to
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Logs an info message
	 * 
	 * @param message
	 *            the message to log
	 */
	public final void info(final String message) {
		logger.info(message);
	}

	/**
	 * Gets the server the bot is connected to
	 * 
	 * @return the server the bot is connected to
	 */
	public final Server getServer() {
		return server;
	}

	/**
	 * Executed just after connecting to the server and before joining channels
	 */
	public abstract void onStart();

	/**
	 * Initialises a bot.
	 * 
	 * @param server
	 *            the server host name to connect to
	 * @param port
	 *            the port number
	 */
	public Bot(final String server, final int port) {
		this.port = port;
		try {
			this.server = new Server(new Socket(server, port));
			this.server.sendRaw("NICK " + getNick() + "\n");
			this.server.sendRaw("USER " + getUser() + " 0 * :" + getRealName());
			if (this instanceof IRCEventListener) {
				this.server.getEventManager().addListener(
						(IRCEventListener) this);
			}
			onStart();
			for (Channel s : getChannels()) {
				s.join();
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Gets the channels to auto connect to
	 * 
	 * @return the channels to auto connect to
	 */
	public abstract Channel[] getChannels();

	/**
	 * Gets the nickname the bot should try to connect with
	 * 
	 * @return the nickname of the bot
	 */
	public abstract String getNick();

	/**
	 * Gets the alternative nickname of the bot
	 * 
	 * @return the alternative nickname of the bot
	 */
	public String getAltNick() {
		return getNick() + "_";
	}

	/**
	 * Gets the real name of the bot
	 * 
	 * @return the real nameof bot
	 */
	public String getRealName() {
		return "SpeedsIrcApi";
	}

	/**
	 * Gets the username of the bot
	 * 
	 * @return the username of bot
	 */
	public String getUser() {
		return "SpeedsIrcApi";
	}

	private void connect() {
		this.server.sendRaw("NICK " + getNick() + "\n");
		this.server.sendRaw("USER " + getUser() + " " + modes + " * :"
				+ getRealName() + "\n");
		for (Channel s : getChannels()) {
			s.join();
		}
	}

	/**
	 * Used to identify to NickServ.
	 * 
	 * @param password
	 *            The password assigned to your nick
	 */
	public void identify(final String password) {
		server.sendRaw("PRIVMSG NickServ :identify " + password + "\n");
	}

	public void apiEventReceived(ApiEvent e) {
		if (e.getOpcode() == ApiEvent.SERVER_DISCONNECTED) {
			connect();
		} else if (e.getOpcode() == ApiEvent.EXCEPTION_RECEIVED) {
			((ExceptionEvent) e).getException().printStackTrace();
		}
	}
}
