package com.speed.irc.connection;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.speed.irc.event.ChannelEvent;
import com.speed.irc.event.ChannelUserEvent;
import com.speed.irc.event.ExceptionEvent;
import com.speed.irc.event.NoticeEvent;
import com.speed.irc.event.PrivateMessageEvent;
import com.speed.irc.event.RawMessageEvent;
import com.speed.irc.types.CTCPReply;
import com.speed.irc.types.Channel;
import com.speed.irc.types.ChannelUser;
import com.speed.irc.types.Conversable;
import com.speed.irc.types.MessageReader;
import com.speed.irc.types.NOTICE;
import com.speed.irc.types.PRIVMSG;
import com.speed.irc.types.ParsingException;
import com.speed.irc.types.RawMessage;
import com.speed.irc.types.ServerUser;
import com.speed.irc.util.Numerics;

/**
 * Processes messages sent from the server.
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
public class ServerMessageParser implements Runnable {
	private final Server server;
	/**
	 * Regex is only being used temporarily, we will return to using split once
	 * I make it less messy.
	 */
	private static final Pattern PATTERN_PRIVMSG = Pattern
			.compile("(.+?)!(.+?)@(.+?) PRIVMSG (#?.+?) :(.*)");
	private static final Pattern PATTERN_NOTICE = Pattern
			.compile("(.+?)!(.+?)@(.+?) NOTICE (#?.+?) :(.*)");
	private Thread thread;
	private List<MessageReader> readers = new CopyOnWriteArrayList<MessageReader>();
	protected volatile boolean running = true;
	protected ServerMessageReader reader;
	public static final CTCPReply CTCP_REPLY_VERSION = new CTCPReply() {

		public String getResponse() {
			return "Speed's IRC API";
		}

		public String getRequest() {
			return "VERSION";
		}

	};

	public static final CTCPReply CTCP_REPLY_TIME = new CTCPReply() {

		public String getResponse() {
			return new Date().toString();
		}

		public String getRequest() {
			return "TIME";
		}

	};

	public ServerMessageParser(final Server server) {
		this.server = server;
		reader = new ServerMessageReader(server);
		new Thread(reader, "Server message reader").start();
		thread = new Thread(this);
		thread.start();

	}

	public synchronized void attach(final MessageReader reader) {
		readers.add(reader);
	}

	private synchronized void parse(final String s) throws Exception {
		for (MessageReader r : readers) {
			if (r.filter.accept(s)) {
				r.s = s;

				synchronized (r) {
					r.notify();

				}
			}
		}
		final RawMessage message = new RawMessage(s);
		String raw = message.getRaw();
		String code = message.getCommand();
		final Matcher priv_matcher = PATTERN_PRIVMSG.matcher(s);
		final Matcher notice_matcher = PATTERN_NOTICE.matcher(s);
		if (s.startsWith("PING")) {
			server.sendRaw("PONG" + s.replaceFirst("PING", "") + "\n");
		} else if (message.getCommand().equals("PRIVMSG")
				&& priv_matcher.matches()) {
			final String msg = priv_matcher.group(5);
			final String sender = priv_matcher.group(1);
			final String user = priv_matcher.group(2);
			final String host = priv_matcher.group(3);
			final String name = priv_matcher.group(4);
			if (msg.startsWith("\u0001")) {
				String request = msg.replace("\u0001", "");
				String reply = server.getCtcpReply(request);
				if (reply != null) {
					server.sendRaw(String.format(
							"NOTICE %s :\u0001%s %s\u0001\n", sender, request,
							reply));
				}
			}
			Conversable conversable = null;
			if (s.contains("PRIVMSG #")) {
				conversable = server.channels.get(name);
			} else {
				conversable = new ServerUser(sender, host, user, server);
			}
			server.eventManager.fireEvent(new PrivateMessageEvent(new PRIVMSG(
					msg, sender, conversable), this));
		} else if (message.getCommand().equals("NOTICE")
				&& notice_matcher.matches()) {
			final String msg = notice_matcher.group(5);
			final String sender = notice_matcher.group(1);
			final String name = notice_matcher.group(4);
			String channel = null;
			if (s.contains("NOTICE #"))
				channel = name;
			server.eventManager.fireEvent(new NoticeEvent(new NOTICE(msg,
					sender, channel), this));

		} else if (message.getCommand().equals(Numerics.SERVER_SUPPORT)) {
			if (s.contains("PREFIX")) {
				String temp = s.substring(0, s.indexOf(" :"));
				String[] parts = temp.split(" ");
				for (String t : parts) {
					if (t.startsWith("PREFIX=")) {
						String letters = t.split("\\(", 2)[1].split("\\)")[0];
						String symbols = t.split("\\)", 2)[1];
						if (letters.length() == symbols.length()) {
							server.setModeLetters(letters.toCharArray());
							server.setModeSymbols(symbols.toCharArray());
						}
					}
				}
			}
		} else if (code.equals("KICK")) {
			final Channel channel = server.channels.get(raw.split(" ")[2]);
			if (channel == null) {
				return;
			}
			final ChannelUser user = channel.getUser(raw.split(" ")[3]);
			if (user == null) {
				return;
			}

			server.getEventManager().fireEvent(
					new ChannelUserEvent(this, channel, user,
							ChannelUserEvent.USER_KICKED));
		} else if (code.equals("PART")) {
			final String nick = message.getSender().split("!")[0];
			Channel channel = server.channels.get(raw.split(" ")[2]);
			if (channel == null) {
				channel = new Channel(raw.split(" ")[2], server);
			}
			final ChannelUser user = channel.getUser(nick);
			server.getEventManager().fireEvent(
					new ChannelUserEvent(this, channel, user,
							ChannelUserEvent.USER_PARTED));
		} else if (code.equals("JOIN")) {
			final String[] parts = raw.split("!");
			final String nick = parts[0];
			final String user = parts[1].split("@")[0];
			final String host = parts[1].split("@")[1].split(" ")[0];
			Channel channel = server.channels.get(raw.split(" ")[2]);
			if (channel == null) {
				channel = new Channel(raw.split(" ")[2], server);
			}
			final ChannelUser u = new ChannelUser(nick, "", user, host, channel);
			server.getEventManager().fireEvent(
					new ChannelUserEvent(this, channel, u,
							ChannelUserEvent.USER_JOINED));
		} else if (code.equals(Numerics.CHANNEL_MODES)) {
			String chan_name = message.getRaw().split(" ")[3];
			String modez = message.getRaw().split(" ")[4];
			if (!server.channels.containsKey(chan_name)) {
				return;
			}
			Channel channel = server.channels.get(chan_name);
			channel.chanMode.parse(modez);
		} else if (code.equals("MODE")) {

			String name = message.getTarget();
			if (!server.channels.containsKey(name)) {
				return;
			}
			Channel channel = server.channels.get(name);
			raw = raw.split(name, 2)[1].trim();
			String[] strings = raw.split(" ");
			String modes = strings[0];
			if (strings.length == 1) {
				channel.chanMode.parse(modes);
				server.getEventManager().fireEvent(
						new ChannelEvent(channel, ChannelEvent.MODE_CHANGED,
								this));
			} else {
				String[] u = new String[strings.length - 1];
				System.arraycopy(strings, 1, u, 0, u.length);
				boolean plus = false;
				int index = 0;
				for (int i = 0; i < modes.toCharArray().length; i++) {
					char c = modes.toCharArray()[i];
					if (c == '+') {
						plus = true;
						continue;
					} else if (c == '-') {
						plus = false;
						continue;
					}
					if (c == 'b') {
						if (plus) {
							channel.bans.add(u[index]);
						} else {
							channel.bans.remove(u[index]);
						}
						server.getEventManager().fireEvent(
								new ChannelEvent(channel,
										ChannelEvent.MODE_CHANGED, this));
						continue;
					}
					ChannelUser user = channel.getUser(u[index]);
					if (user != null) {
						if (plus) {
							user.addMode(c);
						} else {
							user.removeMode(c);
						}
						server.getEventManager().fireEvent(
								new ChannelUserEvent(this, channel, user,
										ChannelUserEvent.USER_MODE_CHANGED));

					}
				}
				index++;

			}

		} else if (code.equals(Numerics.WHO_RESPONSE)) {
			Channel channel = server.channels.get(raw.split(" ")[3]);
			String[] temp = raw.split(" ");
			String user = temp[4];
			String host = temp[5];
			String nick = temp[7];
			String modes = temp[8];
			modes = modes.replace("*", "").replace("G", "").replace("H", "");
			channel.userBuffer.add(new ChannelUser(nick, modes, user, host,
					channel));

		} else if (code.equals(Numerics.WHO_END)) {
			Channel channel = server.channels.get(raw.split(" ")[3]);

			channel.users.clear();
			channel.users.addAll(channel.userBuffer);
			channel.userBuffer.clear();
		} else if (code.toLowerCase().equals("topic")) {
			Channel channel = server.channels.get(raw.split(" ")[2]);
			String[] temp = raw.split(" :", 2);
			if (temp[0].substring(temp[0].indexOf("TOPIC")).contains(
					channel.getName())) {
				server.getEventManager().fireEvent(
						new ChannelEvent(channel, ChannelEvent.TOPIC_CHANGED,
								this));
			}
		} else if (code.equals(Numerics.BANNED_FROM_CHANNEL)
				&& message.getTarget().equals(server.getNick())) {
			Channel channel = server.channels.get(raw.split(" ")[3]);
			if (channel != null && channel.isRunning)
				channel.isRunning = false;
		} else if (code.equals("NICK")) {
			for (Channel channel : server.channels.values()) {
				final ChannelUser user = channel.getUser(message.getSender()
						.split("!")[0]);
				if (user != null) {
					user.setNick(raw.substring(raw.indexOf(": ") + 2).trim());
				}
			}
		}
		server.eventManager.fireEvent(new RawMessageEvent(message, this));

	}

	public void run() {
		String s;
		while (running && server.isConnected()) {
			if (reader.isEmpty()) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				s = reader.poll();
				s = s.substring(1);
				try {
					parse(s);
				} catch (Exception e) {
					server.eventManager.fireEvent(new ExceptionEvent(
							new ParsingException("Parsing error", e), this,
							server));
				}
			}
		}
	}
}