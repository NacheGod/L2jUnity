/*
 * Copyright (C) 2004-2015 L2J Server
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
package org.l2junity.gameserver.model.events.timers;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.l2junity.gameserver.ThreadPoolManager;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.actor.Npc;
import org.l2junity.gameserver.model.actor.instance.PlayerInstance;
import org.l2junity.gameserver.model.events.TimerExecutor;

/**
 * @author UnAfraid
 * @param <T>
 */
public class TimerHolder<T> implements Runnable
{
	private final T _event;
	private final StatsSet _params;
	private final long _time;
	private final Npc _npc;
	private final PlayerInstance _player;
	private final boolean _isRepeating;
	private final IEventTimer<T> _script;
	private final TimerExecutor<T> _postExecutor;
	private final ScheduledFuture<?> _task;
	
	public TimerHolder(T event, StatsSet params, long time, Npc npc, PlayerInstance player, boolean isRepeating, IEventTimer<T> script, TimerExecutor<T> postExecutor)
	{
		Objects.requireNonNull(event, getClass().getSimpleName() + ": \"event\" cannot be null!");
		Objects.requireNonNull(script, getClass().getSimpleName() + ": \"script\" cannot be null!");
		Objects.requireNonNull(postExecutor, getClass().getSimpleName() + ": \"postExecutor\" cannot be null!");
		_event = event;
		_params = params;
		_time = time;
		_npc = npc;
		_player = player;
		_isRepeating = isRepeating;
		_script = script;
		_postExecutor = postExecutor;
		_task = _isRepeating ? ThreadPoolManager.getInstance().scheduleEventAtFixedRate(this, _time, _time) : ThreadPoolManager.getInstance().scheduleEvent(this, _time);
	}
	
	/**
	 * @return the event/key of this timer
	 */
	public T getEvent()
	{
		return _event;
	}
	
	/**
	 * @return the parameters of this timer
	 */
	public StatsSet getParams()
	{
		return _params;
	}
	
	/**
	 * @return the npc of this timer
	 */
	public Npc getNpc()
	{
		return _npc;
	}
	
	/**
	 * @return the player of this timer
	 */
	public PlayerInstance getPlayer()
	{
		return _player;
	}
	
	/**
	 * @return {@code true} if the timer will repeat itself, {@code false} otherwise
	 */
	public boolean isRepeating()
	{
		return _isRepeating;
	}
	
	/**
	 * @return {@code true} if timer for the given event, npc, player were stopped, {@code false} otheriwse
	 */
	public boolean cancelTimer()
	{
		if (_task.isCancelled() || _task.isDone())
		{
			return false;
		}
		
		_task.cancel(false);
		return true;
	}
	
	/**
	 * @return the remaining time of the timer, or -1 in case it doesn't exists.
	 */
	public long getRemainingTime()
	{
		if (_task == null)
		{
			return -1;
		}
		
		if (_task.isCancelled() || _task.isDone())
		{
			return -1;
		}
		return _task.getDelay(TimeUnit.MILLISECONDS);
	}
	
	/**
	 * @param event
	 * @param npc
	 * @param player
	 * @return {@code true} if event, npc, player are equals to the ones stored in this TimerHolder, {@code false} otherwise
	 */
	public boolean isEqual(T event, Npc npc, PlayerInstance player)
	{
		return _event.equals(event) && (_npc == npc) && (_player == player);
	}
	
	@Override
	public void run()
	{
		// Notify the script that the event has been fired.
		_script.onTimerEvent(this);
		
		// Notify the post executor to remove this timer from the map
		_postExecutor.onTimerPostExecute(this);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof TimerHolder))
		{
			return false;
		}
		
		@SuppressWarnings("unchecked")
		final TimerHolder<T> holder = (TimerHolder<T>) obj;
		return isEqual(holder._event, holder._npc, holder._player);
	}
}