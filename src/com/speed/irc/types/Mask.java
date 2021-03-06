package com.speed.irc.types;

/**
 * Class used to encapsulate user masks
 * 
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
public class Mask {

	private final String mask;

	/**
	 * Initialise the user mask
	 * 
	 * @param mask
	 *            the mask to use
	 */
	public Mask(final String mask) {
		this.mask = mask;
		if (!verify(mask))
			throw new IllegalArgumentException("Mask doesn't match *!*@*");
	}

	public Mask(final String nick, final String user, final String host) {
		this.mask = nick + '!' + user + '@' + host;
		if (!verify(mask))
			throw new IllegalArgumentException("Arguments are not valid");
	}

	/**
	 * Verifies if the mask is valid
	 * 
	 * @param mask
	 *            the mask to check
	 * @return <tt>true</tt> if the mask is valid, <tt>false</tt> if it isn't.
	 */
	public static boolean verify(final String mask) {
		return mask.matches("[\\w\\*]+?![\\*\\w]+?@[\\*\\w\\.]+?");
	}

	/**
	 * Checks if a user matches this mask.
	 * 
	 * @param user
	 *            the user to check
	 * @return <tt>true</tt> if they do match, <tt>false</tt> if they don't
	 */
	public boolean matches(ServerUser user) {
		// do the nick
		String nickMask = mask.substring(0, mask.indexOf('!')).replace("*",
				".*");
		String userMask = mask.substring(mask.indexOf('!') + 1,
				mask.indexOf('@')).replace("*", ".*");
		String hostMask = mask.substring(mask.indexOf('@') + 1)
				.replace(".", "\\.").replace("*", ".*");
		return user.getNick().matches(nickMask)
				&& user.getUser().matches(userMask)
				&& user.getHost().matches(hostMask);
	}

	public boolean equals(Object o) {
		return o instanceof Mask && ((Mask) o).mask == mask;
	}
}
