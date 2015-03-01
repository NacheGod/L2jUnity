/*
 * Copyright (C) 2004-2014 L2J Server
 *
 * This file is part of L2J Server.
 *
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2junity.loginserver.controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2junity.loginserver.Config;
import org.l2junity.loginserver.DatabaseFactory;
import org.l2junity.loginserver.db.AccountBansDAO;
import org.l2junity.loginserver.db.AccountLoginsDAO;
import org.l2junity.loginserver.db.AccountsDAO;
import org.l2junity.loginserver.db.dto.Account;
import org.l2junity.loginserver.network.client.ClientHandler;
import org.l2junity.loginserver.network.client.ConnectionState;
import org.l2junity.loginserver.network.client.send.BlockedAccount;
import org.l2junity.loginserver.network.client.send.LoginFail2;
import org.l2junity.loginserver.network.client.send.LoginOk;
import org.l2junity.loginserver.network.client.send.LoginOtpFail;
import org.l2junity.loginserver.network.client.send.ServerList;
import org.skife.jdbi.v2.exceptions.DBIException;

/**
 * @author UnAfraid
 */
public class LoginClientController
{
	private final Logger _log = Logger.getLogger(LoginClientController.class.getName());
	
	private MessageDigest _passwordHashCrypt;
	private final AtomicInteger _connectionId = new AtomicInteger();
	
	protected LoginClientController()
	{
		try
		{
			_passwordHashCrypt = MessageDigest.getInstance("SHA");
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, getClass().getSimpleName() + ": Failed initializing: " + e.getMessage(), e);
		}
	}
	
	@SuppressWarnings("unused")
	public void tryAuthLogin(ClientHandler client, String name, String password, int otp)
	{
		try (AccountsDAO accountsDAO = DatabaseFactory.getInstance().getAccountsDAO())
		{
			final String passwordHashBase64 = Base64.getEncoder().encodeToString(_passwordHashCrypt.digest(password.getBytes(StandardCharsets.UTF_8)));
			Account account = accountsDAO.findByName(name);
			if ((account == null) && Config.AUTO_CREATE_ACCOUNTS)
			{
				long accountId = accountsDAO.insert(name, passwordHashBase64);
				account = accountsDAO.findById(accountId);
				_log.info("Auto created account '" + name + "'.");
			}
			
			if ((account == null) || !account.getPassword().equals(passwordHashBase64))
			{
				client.close(LoginFail2.THE_USERNAME_AND_PASSWORD_DO_NOT_MATCH_PLEASE_CHECK_YOUR_ACCOUNT_INFORMATION_AND_TRY_LOGGING_IN_AGAIN);
				return;
			}
			
			// TODO: check OTP
			if (false)
			{
				client.close(new LoginOtpFail());
				return;
			}
			
			try (AccountBansDAO accountBansDAO = DatabaseFactory.getInstance().getAccountBansDAO())
			{
				if (!accountBansDAO.findActiveByAccountId(account).isEmpty())
				{
					client.close(BlockedAccount.YOUR_ACCOUNT_HAS_BEEN_RESTRICTED_IN_ACCORDANCE_WITH_OUR_TERMS_OF_SERVICE_DUE_TO_YOUR_CONFIRMED_ABUSE_OF_IN_GAME_SYSTEMS_RESULTING_IN_ABNORMAL_GAMEPLAY_FOR_MORE_DETAILS_PLEASE_VISIT_THE_LINEAGE_II_SUPPORT_WEBSITE_HTTPS_SUPPORT_LINEAGE2_COM);
					return;
				}
			}
			
			// TODO: check if account is already logged in
			if (false)
			{
				// TODO: kick account
				client.close(LoginFail2.ACCOUNT_IS_ALREADY_IN_USE);
				return;
			}
			
			try (AccountLoginsDAO accountLoginsDAO = DatabaseFactory.getInstance().getAccountLoginsDAO())
			{
				client.setAccountLoginsId(accountLoginsDAO.insert(account, client.getInetAddress().toString()));
			}
			
			if (Config.SHOW_LICENCE)
			{
				client.setConnectionState(ConnectionState.AUTHED_LICENCE);
				client.sendPacket(new LoginOk(client.getLoginSessionId()));
			}
			else
			{
				client.setConnectionState(ConnectionState.AUTHED_SERVER_LIST);
				client.sendPacket(new ServerList());
			}
		}
		catch (DBIException e)
		{
			_log.log(Level.WARNING, "There was an error while logging in Name: " + name + " Password: " + password + " OTP: " + otp);
			client.close(LoginFail2.SYSTEM_ERROR);
		}
	}
	
	/**
	 * @param serverId
	 * @param client
	 */
	public void tryGameLogin(byte serverId, ClientHandler client)
	{
		try (AccountLoginsDAO accountLoginsDAO = DatabaseFactory.getInstance().getAccountLoginsDAO())
		{
			accountLoginsDAO.updateServerId(client.getAccountLoginsId(), (short) (serverId & 0xFF));
		}
	}
	
	/**
	 * @return
	 */
	public int getNextConnectionId()
	{
		return _connectionId.getAndIncrement();
	}
	
	public static LoginClientController getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static final class SingletonHolder
	{
		protected static final LoginClientController _instance = new LoginClientController();
	}
}
