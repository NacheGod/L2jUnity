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
package org.l2junity.gameserver.model.instancezone;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.l2junity.Config;
import org.l2junity.DatabaseFactory;
import org.l2junity.commons.util.CommonUtil;
import org.l2junity.gameserver.ThreadPoolManager;
import org.l2junity.gameserver.data.xml.impl.DoorData;
import org.l2junity.gameserver.enums.InstanceReenterType;
import org.l2junity.gameserver.enums.InstanceTeleportType;
import org.l2junity.gameserver.instancemanager.InstanceManager;
import org.l2junity.gameserver.model.L2Spawn;
import org.l2junity.gameserver.model.Location;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.TeleportWhereType;
import org.l2junity.gameserver.model.World;
import org.l2junity.gameserver.model.WorldObject;
import org.l2junity.gameserver.model.actor.Npc;
import org.l2junity.gameserver.model.actor.Summon;
import org.l2junity.gameserver.model.actor.instance.L2DoorInstance;
import org.l2junity.gameserver.model.actor.instance.PlayerInstance;
import org.l2junity.gameserver.model.actor.templates.L2DoorTemplate;
import org.l2junity.gameserver.model.events.EventDispatcher;
import org.l2junity.gameserver.model.events.impl.instance.OnInstanceCreated;
import org.l2junity.gameserver.model.events.impl.instance.OnInstanceDestroy;
import org.l2junity.gameserver.model.events.impl.instance.OnInstanceEnter;
import org.l2junity.gameserver.model.events.impl.instance.OnInstanceLeave;
import org.l2junity.gameserver.model.holders.SpawnHolder;
import org.l2junity.gameserver.model.interfaces.IIdentifiable;
import org.l2junity.gameserver.model.interfaces.ILocational;
import org.l2junity.gameserver.model.interfaces.INamable;
import org.l2junity.gameserver.network.client.send.IClientOutgoingPacket;
import org.l2junity.gameserver.network.client.send.SystemMessage;
import org.l2junity.gameserver.network.client.send.string.SystemMessageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instance world.
 * @author malyelfik
 */
public final class Instance implements IIdentifiable, INamable
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Instance.class);
	
	// Basic instance parameters
	private final int _id;
	private final InstanceTemplate _template;
	private final long _startTime;
	private long _endTime;
	// Advanced instance parameters
	private final Set<Integer> _allowed = ConcurrentHashMap.newKeySet(); // ObjectId of players which can enter to instance
	private final Set<PlayerInstance> _players = ConcurrentHashMap.newKeySet(); // Players inside instance
	private final Set<Npc> _npcs = ConcurrentHashMap.newKeySet(); // Spawned NPCs inside instance
	private final Map<Integer, L2DoorInstance> _doors = new HashMap<>(); // Spawned doors inside instance
	private final StatsSet _parameters = new StatsSet();
	// Timers
	private final Map<Integer, ScheduledFuture<?>> _ejectDeadTasks = new ConcurrentHashMap<>();
	private ScheduledFuture<?> _cleanUpTask = null;
	private ScheduledFuture<?> _emptyDestroyTask = null;
	
	/**
	 * Create instance world.
	 * @param id ID of instance world
	 * @param template template of instance world
	 */
	public Instance(int id, InstanceTemplate template)
	{
		// Set basic instance info
		_id = id;
		_template = template;
		_startTime = System.currentTimeMillis();
		
		// Register world to instance manager.
		InstanceManager.getInstance().register(this);
		
		// Set duration, spawns, status, etc..
		setDuration(_template.getDuration());
		setStatus(0);
		spawnDoors();
		if (isSpawnGroupExist("general"))
		{
			spawnGroup("general");
		}
		
		if (!isDynamic())
		{
			// Notify DP scripts
			EventDispatcher.getInstance().notifyEventAsync(new OnInstanceCreated(this), _template);
		}
		
		// Debug logger
		if (Config.DEBUG_INSTANCES)
		{
			LOGGER.info("Instance {} ({}) has been created with instance id {}.", _template.getName(), _template.getId(), getId());
		}
	}
	
	@Override
	public int getId()
	{
		return _id;
	}
	
	@Override
	public String getName()
	{
		return _template.getName();
	}
	
	/**
	 * Check if instance has been created dynamically or have XML template.
	 * @return {@code true} if instance is dynamic or {@code false} if instance has static template
	 */
	public boolean isDynamic()
	{
		return _template.getId() == -1;
	}
	
	/**
	 * Set instance world parameter.
	 * @param key parameter name
	 * @param val parameter value
	 */
	public void setParameter(String key, Object val)
	{
		if (val == null)
		{
			_parameters.remove(key);
		}
		else
		{
			_parameters.set(key, val);
		}
	}
	
	/**
	 * Get instance world parameters.
	 * @return instance parameters
	 */
	public StatsSet getParameters()
	{
		return _parameters;
	}
	
	/**
	 * Get status of instance world.
	 * @return instance status, otherwise 0
	 */
	public int getStatus()
	{
		return _parameters.getInt("INSTANCE_STATUS", 0);
	}
	
	/**
	 * Check if instance status is equal to {@code status}.
	 * @param status number used for status comparison
	 * @return {@code true} when instance status and {@code status} are equal, otherwise {@code false}
	 */
	public boolean isStatus(int status)
	{
		return getStatus() == status;
	}
	
	/**
	 * Set status of instance world.
	 * @param value new world status
	 */
	public void setStatus(int value)
	{
		_parameters.set("INSTANCE_STATUS", value);
	}
	
	/**
	 * Increment instance world status
	 * @return new world status
	 */
	public int incStatus()
	{
		final int status = getStatus() + 1;
		setStatus(status);
		return status;
	}
	
	/**
	 * Add player who can enter to instance.
	 * @param player player instance
	 */
	public void addAllowed(PlayerInstance player)
	{
		_allowed.add(player.getObjectId());
	}
	
	/**
	 * Check if player can enter to instance.
	 * @param player player itself
	 * @return {@code true} when can enter, otherwise {@code false}
	 */
	public boolean isAllowed(PlayerInstance player)
	{
		return _allowed.contains(player.getObjectId());
	}
	
	/**
	 * Returns all players who can enter to instance.
	 * @return allowed players list
	 */
	public Set<Integer> getAllowed()
	{
		return _allowed;
	}
	
	/**
	 * Remove player from allowed so he can't enter anymore.
	 * @param objectId object id of player
	 */
	public void removeAllowed(int objectId)
	{
		_allowed.remove(objectId);
	}
	
	/**
	 * Add player to instance
	 * @param player player instance
	 */
	public void addPlayer(PlayerInstance player)
	{
		_players.add(player);
		if (_emptyDestroyTask != null)
		{
			_emptyDestroyTask.cancel(false);
			_emptyDestroyTask = null;
		}
	}
	
	/**
	 * Remove player from instance.
	 * @param player player instance
	 */
	public void removePlayer(PlayerInstance player)
	{
		_players.remove(player);
		if (_players.isEmpty())
		{
			final long emptyTime = _template.getEmptyDestroyTime();
			if ((_template.getDuration() == 0) || (emptyTime == 0))
			{
				destroy();
			}
			else if ((emptyTime >= 0) && (_emptyDestroyTask == null) && (getRemainingTime() < emptyTime))
			{
				_emptyDestroyTask = ThreadPoolManager.getInstance().scheduleGeneral(this::destroy, emptyTime);
			}
		}
	}
	
	/**
	 * Check if player is inside instance.
	 * @param player player to be checked
	 * @return {@code true} if player is inside, otherwise {@code false}
	 */
	public boolean containsPlayer(PlayerInstance player)
	{
		return _players.contains(player);
	}
	
	/**
	 * Get all players inside instance.
	 * @return players within instance
	 */
	public Set<PlayerInstance> getPlayers()
	{
		return _players;
	}
	
	/**
	 * Get count of players inside instance.
	 * @return players count inside instance
	 */
	public int getPlayersCount()
	{
		return _players.size();
	}
	
	/**
	 * Get first found player from instance world.<br>
	 * <i>This method is useful for instances with one player inside.</i>
	 * @return first found player, otherwise {@code null}
	 */
	public PlayerInstance getFirstPlayer()
	{
		return _players.stream().findFirst().orElse(null);
	}
	
	/**
	 * Get all players from instance world inside specified radius.
	 * @param object location of target
	 * @param radius radius around target
	 * @return players within radius
	 */
	public Set<PlayerInstance> getPlayersInsideRadius(ILocational object, int radius)
	{
		return _players.stream().filter(p -> p.isInsideRadius(object, radius, true, true)).collect(Collectors.toSet());
	}
	
	/**
	 * Spawn doors inside instance world.
	 */
	private void spawnDoors()
	{
		for (Entry<Integer, Boolean> entry : _template.getDoors().entrySet())
		{
			// Load door template
			final int doorId = entry.getKey();
			final StatsSet templ = DoorData.getInstance().getDoorTemplate(doorId);
			if (templ == null)
			{
				LOGGER.warn("Cannot find template for doors {}, instance {} ({})", doorId, getName(), getTemplateId());
				continue;
			}
			
			// Create new door instance
			final L2DoorInstance door = new L2DoorInstance(new L2DoorTemplate(templ));
			door.setInstance(this);
			door.setCurrentHp(door.getMaxHp());
			door.spawnMe(door.getTemplate().getX(), door.getTemplate().getY(), door.getTemplate().getZ());
			_doors.put(doorId, door);
			
			// Check if doors should be opened/closed
			final Boolean openStatus = entry.getValue();
			if (openStatus != null)
			{
				if (openStatus)
				{
					door.openMe();
				}
				else
				{
					door.closeMe();
				}
			}
		}
	}
	
	/**
	 * Get all doors spawned inside instance world.
	 * @return collection of spawned doors
	 */
	public Collection<L2DoorInstance> getDoors()
	{
		return _doors.values();
	}
	
	/**
	 * Get spawned door by template ID.
	 * @param id template ID of door
	 * @return instance of door if found, otherwise {@code null}
	 */
	public L2DoorInstance getDoor(int id)
	{
		return _doors.get(id);
	}
	
	/**
	 * Handle open/close status of instance doors.
	 * @param id ID of doors
	 * @param open {@code true} means open door, {@code false} means close door
	 */
	public void openCloseDoor(int id, boolean open)
	{
		final L2DoorInstance door = _doors.get(id);
		if (door != null)
		{
			if (open)
			{
				if (!door.getOpen())
				{
					door.openMe();
				}
			}
			else
			{
				if (door.getOpen())
				{
					door.closeMe();
				}
			}
		}
	}
	
	/**
	 * Check if spawn group with name {@code name} exists.
	 * @param name name of group to be checked
	 * @return {@code true} if group exist, otherwise {@code false}
	 */
	public boolean isSpawnGroupExist(String name)
	{
		return _template.getSpawnGroup(name) != null;
	}
	
	/**
	 * Spawn NPCs from group (defined in XML template) into instance world.
	 * @param name name of group which should be spawned
	 * @return list that contains NPCs spawned by this method
	 */
	public List<Npc> spawnGroup(String name)
	{
		final List<SpawnHolder> spawns = _template.getSpawnGroup(name);
		if (spawns == null)
		{
			LOGGER.warn("Spawn group {} doesn't exist for instance {} ({})!", name, getName(), _id);
			return Collections.emptyList();
		}
		
		final List<Npc> npcs = new ArrayList<>(spawns.size());
		try
		{
			for (SpawnHolder holder : spawns)
			{
				final L2Spawn spawn = new L2Spawn(holder.getNpcId());
				spawn.setX(holder.getX());
				spawn.setY(holder.getY());
				spawn.setZ(holder.getZ());
				spawn.setHeading(holder.getHeading());
				spawn.setAmount(1);
				spawn.setRespawnDelay(holder.getRespawnDelay());
				spawn.setInstanceId(_id);
				if (holder.getRespawnDelay() > 0)
				{
					spawn.startRespawn();
				}
				else
				{
					spawn.stopRespawn();
				}
				npcs.add(spawn.doSpawn());
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Unable to spawn group {} inside instance {} ({})", name, getName(), _id);
		}
		return npcs;
	}
	
	/**
	 * Get spawned NPCs from instance.
	 * @return set of NPCs from instance
	 */
	public Set<Npc> getNpcs()
	{
		return _npcs;
	}
	
	/**
	 * Get alive NPCs from instance.
	 * @return set of NPCs from instance
	 */
	public Set<Npc> getAliveNpcs()
	{
		return _npcs.stream().filter(n -> !n.isDead()).collect(Collectors.toSet());
	}
	
	/**
	 * Get spawned NPCs from instance with specific IDs.
	 * @param id IDs of NPCs which should be found
	 * @return list of filtered NPCs from instance
	 */
	public List<Npc> getNpcs(int... id)
	{
		return _npcs.stream().filter(n -> CommonUtil.contains(id, n.getId())).collect(Collectors.toList());
	}
	
	/**
	 * Get alive NPCs from instance with specific IDs.
	 * @param id IDs of NPCs which should be found
	 * @return list of filtered NPCs from instance
	 */
	public List<Npc> getAliveNpcs(int... id)
	{
		return _npcs.stream().filter(n -> !n.isDead() && CommonUtil.contains(id, n.getId())).collect(Collectors.toList());
	}
	
	/**
	 * Get first found spawned NPC with specific ID.
	 * @param id ID of NPC to be found
	 * @return first found NPC with specified ID, otherwise {@code null}
	 */
	public Npc getNpc(int id)
	{
		return _npcs.stream().filter(n -> n.getId() == id).findFirst().orElse(null);
	}
	
	public void addNpc(Npc npc)
	{
		_npcs.add(npc);
	}
	
	public void removeNpc(Npc npc)
	{
		_npcs.remove(npc);
	}
	
	/**
	 * Remove all players from instance world.
	 */
	private void removePlayers()
	{
		_players.forEach(this::ejectPlayer);
		_players.clear();
	}
	
	/**
	 * Despawn doors inside instance world.
	 */
	private void removeDoors()
	{
		_doors.values().stream().filter(Objects::nonNull).forEach(L2DoorInstance::decayMe);
		_doors.clear();
	}
	
	/**
	 * Despawn NPCs inside instance world.
	 */
	public void removeNpcs()
	{
		for (Npc mob : _npcs)
		{
			if (mob != null)
			{
				if (mob.getSpawn() != null)
				{
					mob.getSpawn().stopRespawn();
				}
				mob.deleteMe();
			}
		}
		_npcs.clear();
	}
	
	/**
	 * Change instance duration.
	 * @param minutes remaining time to destroy instance
	 */
	public void setDuration(int minutes)
	{
		// Instance never ends
		if (minutes < 0)
		{
			_endTime = -1;
			return;
		}
		
		// Stop running tasks
		final long millis = TimeUnit.MINUTES.toMillis(minutes);
		if (_cleanUpTask != null)
		{
			_cleanUpTask.cancel(true);
			_cleanUpTask = null;
		}
		
		if ((_emptyDestroyTask != null) && (millis < _emptyDestroyTask.getDelay(TimeUnit.MILLISECONDS)))
		{
			_emptyDestroyTask.cancel(true);
			_emptyDestroyTask = null;
		}
		
		// Set new cleanup task
		_endTime = System.currentTimeMillis() + millis;
		if (minutes < 1) // Destroy instance
		{
			destroy();
		}
		else
		{
			sendWorldDestroyMessage(minutes);
			if (minutes <= 5) // Message 1 minute before destroy
			{
				_cleanUpTask = ThreadPoolManager.getInstance().scheduleGeneral(this::cleanUp, millis - 60000);
			}
			else // Message 5 minutes before destroy
			{
				_cleanUpTask = ThreadPoolManager.getInstance().scheduleGeneral(this::cleanUp, millis - (5 * 60000));
			}
		}
	}
	
	/**
	 * Destroy current instance world.<br>
	 * <b><font color=red>Use this method to destroy instance world properly.</font></b>
	 */
	public synchronized void destroy()
	{
		if (_cleanUpTask != null)
		{
			_cleanUpTask.cancel(false);
			_cleanUpTask = null;
		}
		
		if (_emptyDestroyTask != null)
		{
			_emptyDestroyTask.cancel(false);
			_emptyDestroyTask = null;
		}
		
		_ejectDeadTasks.values().forEach(t -> t.cancel(true));
		_ejectDeadTasks.clear();
		
		// Notify DP scripts
		if (!isDynamic())
		{
			EventDispatcher.getInstance().notifyEvent(new OnInstanceDestroy(this), _template);
		}
		
		removePlayers();
		removeDoors();
		removeNpcs();
		
		InstanceManager.getInstance().unregister(getId());
	}
	
	/**
	 * Teleport player out of instance.
	 * @param player player that should be moved out
	 */
	public void ejectPlayer(PlayerInstance player)
	{
		if (player.getInstanceWorld().equals(this))
		{
			final Location loc = _template.getExitLocation(player);
			if (loc != null)
			{
				player.teleToLocation(loc, null);
			}
			else
			{
				player.teleToLocation(TeleportWhereType.TOWN, null);
			}
		}
	}
	
	/**
	 * Send packet to each player from instance world.
	 * @param packets packets to be send
	 */
	public void broadcastPacket(IClientOutgoingPacket... packets)
	{
		for (PlayerInstance player : _players)
		{
			for (IClientOutgoingPacket packet : packets)
			{
				player.sendPacket(packet);
			}
		}
	}
	
	/**
	 * Get instance creation time.
	 * @return creation time in milliseconds
	 */
	public long getStartTime()
	{
		return _startTime;
	}
	
	/**
	 * Get elapsed time since instance create.
	 * @return elapsed time in milliseconds
	 */
	public long getElapsedTime()
	{
		return System.currentTimeMillis() - _startTime;
	}
	
	/**
	 * Get remaining time before instance will be destroyed.
	 * @return remaining time in milliseconds if duration is not equal to -1, otherwise -1
	 */
	public long getRemainingTime()
	{
		return (_endTime == -1) ? -1 : (_endTime - System.currentTimeMillis());
	}
	
	/**
	 * Get instance destroy time.
	 * @return destroy time in milliseconds if duration is not equal to -1, otherwise -1
	 */
	public long getEndTime()
	{
		return _endTime;
	}
	
	/**
	 * Set reenter penalty for players associated with current instance.<br>
	 * Penalty time is calculated from XML reenter data.
	 */
	public void setReenterTime()
	{
		setReenterTime(_template.calculateReenterTime());
	}
	
	/**
	 * Set reenter penalty for players associated with current instance.<br>
	 * @param time penalty time in milliseconds since January 1, 1970
	 */
	public void setReenterTime(long time)
	{
		// Cannot store reenter data for instance without template id.
		if ((getTemplateId() == -1) && (time > 0))
		{
			return;
		}
		
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO character_instance_time (charId,instanceId,time) VALUES (?,?,?)"))
		{
			// Save to database
			for (Integer objId : _allowed)
			{
				ps.setInt(1, objId);
				ps.setInt(2, getTemplateId());
				ps.setLong(3, time);
				ps.addBatch();
			}
			ps.executeBatch();
			
			// Save to memory and send message to player
			final SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.INSTANT_ZONE_S1_S_ENTRY_HAS_BEEN_RESTRICTED_YOU_CAN_CHECK_THE_NEXT_POSSIBLE_ENTRY_TIME_BY_USING_THE_COMMAND_INSTANCEZONE);
			if (InstanceManager.getInstance().getInstanceName(getTemplateId()) != null)
			{
				msg.addInstanceName(getTemplateId());
			}
			else
			{
				msg.addString(getName());
			}
			_allowed.forEach(objId ->
			{
				InstanceManager.getInstance().setReenterPenalty(objId, getTemplateId(), time);
				final PlayerInstance player = World.getInstance().getPlayer(objId);
				if ((player != null) && player.isOnline())
				{
					player.sendPacket(msg);
				}
			});
		}
		catch (Exception e)
		{
			LOGGER.warn("Could not insert character instance reenter data: ", e);
		}
	}
	
	/**
	 * Set instance world to finish state.<br>
	 * Calls method {@link Instance#finishInstance(int)} with {@link Config#INSTANCE_FINISH_TIME} as argument.<br>
	 * See {@link Instance#finishInstance(int)} for more details.
	 */
	public void finishInstance()
	{
		finishInstance(Config.INSTANCE_FINISH_TIME);
	}
	
	/**
	 * Set instance world to finish state.<br>
	 * Set re-enter for allowed players if required data are defined in template.<br>
	 * Change duration of instance and set empty destroy time to 0 (instant effect).
	 * @param delay delay in minutes
	 */
	public void finishInstance(int delay)
	{
		// Set re-enter for players
		if (_template.getReenterType().equals(InstanceReenterType.ON_FINISH))
		{
			setReenterTime();
		}
		// Change instance duration
		setDuration(delay);
	}
	
	// ---------------------------------------------
	// Listeners
	// ---------------------------------------------
	/**
	 * This method is called when player dead inside instance.
	 * @param player
	 */
	public void onDeath(PlayerInstance player)
	{
		// Send message
		final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.IF_YOU_ARE_NOT_RESURRECTED_WITHIN_S1_MINUTE_S_YOU_WILL_BE_EXPELLED_FROM_THE_INSTANT_ZONE);
		sm.addInt(_template.getEjectTime());
		player.sendPacket(sm);
		
		// Start eject task
		_ejectDeadTasks.put(player.getObjectId(), ThreadPoolManager.getInstance().scheduleGeneral(() ->
		{
			if (player.isDead())
			{
				ejectPlayer(player.getActingPlayer());
			}
		} , _template.getEjectTime(), TimeUnit.MINUTES));
	}
	
	/**
	 * This method is called when player was resurrected inside instance.
	 * @param player resurrected player
	 */
	public void doRevive(PlayerInstance player)
	{
		final ScheduledFuture<?> task = _ejectDeadTasks.remove(player.getObjectId());
		if (task != null)
		{
			task.cancel(true);
		}
	}
	
	/**
	 * This method is called when object enter or leave this instance.
	 * @param object instance of object which enters/leaves instance
	 * @param enter {@code true} when object enter, {@code false} when object leave
	 */
	public void onInstanceChange(WorldObject object, boolean enter)
	{
		if (object.isPlayer())
		{
			final PlayerInstance player = object.getActingPlayer();
			if (enter)
			{
				addPlayer(player);
				
				// Set origin return location if enabled
				if (_template.getExitLocationType().equals(InstanceTeleportType.ORIGIN))
				{
					player.getVariables().set("INSTANCE_ORIGIN", player.getX() + ";" + player.getY() + ";" + player.getZ());
				}
				
				// Remove player buffs
				if (_template.isRemoveBuffEnabled())
				{
					_template.removePlayerBuff(player);
				}
				
				// Notify DP scripts
				if (!isDynamic())
				{
					EventDispatcher.getInstance().notifyEventAsync(new OnInstanceEnter(player, this), _template);
				}
			}
			else
			{
				removePlayer(player);
				// Notify DP scripts
				if (!isDynamic())
				{
					EventDispatcher.getInstance().notifyEventAsync(new OnInstanceLeave(player, this), _template);
				}
			}
		}
		else if (object.isNpc())
		{
			final Npc npc = (Npc) object;
			if (enter)
			{
				addNpc(npc);
			}
			else
			{
				if (npc.getSpawn() != null)
				{
					npc.getSpawn().stopRespawn();
				}
				removeNpc(npc);
			}
		}
	}
	
	/**
	 * This method is called when player logout inside instance world.
	 * @param player player who logout
	 */
	public void onPlayerLogout(PlayerInstance player)
	{
		removePlayer(player);
		if (Config.RESTORE_PLAYER_INSTANCE)
		{
			player.getVariables().set("INSTANCE_RESTORE", getId());
		}
		else
		{
			final Location loc = getExitLocation(player);
			if (loc != null)
			{
				player.setLocationInvisible(loc);
				// If player has death pet, put him out of instance world
				final Summon pet = player.getPet();
				if (pet != null)
				{
					pet.teleToLocation(loc, true);
				}
			}
		}
	}
	
	// ----------------------------------------------
	// Template methods
	// ----------------------------------------------
	/**
	 * Get parameters from instance template.<br>
	 * @return template parameters
	 */
	public StatsSet getTemplateParameters()
	{
		return _template.getParameters();
	}
	
	/**
	 * Get template ID of instance world.
	 * @return instance template ID
	 */
	public int getTemplateId()
	{
		return _template.getId();
	}
	
	/**
	 * Get type of re-enter data.
	 * @return type of re-enter (see {@link InstanceReenterType} for possible values)
	 */
	public InstanceReenterType getReenterType()
	{
		return _template.getReenterType();
	}
	
	/**
	 * Check if instance world is PvP zone.
	 * @return {@code true} when instance is PvP zone, otherwise {@code false}
	 */
	public boolean isPvP()
	{
		return _template.isPvP();
	}
	
	/**
	 * Check if summoning players to instance world is allowed.
	 * @return {@code true} when summon is allowed, otherwise {@code false}
	 */
	public boolean isPlayerSummonAllowed()
	{
		return _template.isPlayerSummonAllowed();
	}
	
	/**
	 * Get enter location for instance world.
	 * @return {@link Location} object if instance has enter location defined, otherwise {@code null}
	 */
	public Location getEnterLocation()
	{
		return _template.getEnterLocation();
	}
	
	/**
	 * Get all enter locations defined in XML template.
	 * @return list of enter locations
	 */
	public List<Location> getEnterLocations()
	{
		return _template.getEnterLocations();
	}
	
	/**
	 * Get exit location for player from instance world.
	 * @param player instance of player who wants to leave instance world
	 * @return {@link Location} object if instance has exit location defined, otherwise {@code null}
	 */
	public Location getExitLocation(PlayerInstance player)
	{
		return _template.getExitLocation(player);
	}
	
	// ----------------------------------------------
	// Tasks
	// ----------------------------------------------
	/**
	 * Clean up instance.
	 */
	private void cleanUp()
	{
		if (getRemainingTime() <= TimeUnit.MINUTES.toMillis(1))
		{
			sendWorldDestroyMessage(1);
			_cleanUpTask = ThreadPoolManager.getInstance().scheduleGeneral(this::destroy, 1, TimeUnit.MINUTES);
		}
		else
		{
			sendWorldDestroyMessage(5);
			_cleanUpTask = ThreadPoolManager.getInstance().scheduleGeneral(this::cleanUp, 5, TimeUnit.MINUTES);
		}
	}
	
	/**
	 * Show instance destroy messages to players inside instance world.
	 * @param delay time in minutes
	 */
	private void sendWorldDestroyMessage(int delay)
	{
		final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.THIS_INSTANT_ZONE_WILL_BE_TERMINATED_IN_S1_MINUTE_S_YOU_WILL_BE_FORCED_OUT_OF_THE_DUNGEON_WHEN_THE_TIME_EXPIRES);
		sm.addInt(delay);
		broadcastPacket(sm);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		return (obj != null) && (obj instanceof Instance) && (((Instance) obj).getId() == getId());
	}
	
	@Override
	public String toString()
	{
		return getName() + "(" + getId() + ")";
	}
}