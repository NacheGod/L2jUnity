/*
 * Copyright (C) 2004-2015 L2J DataPack
 * 
 * This file is part of L2J DataPack.
 * 
 * L2J DataPack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J DataPack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ai.npc.TersisHerald;

import java.util.ArrayList;
import java.util.List;

import org.l2junity.gameserver.enums.ChatType;
import org.l2junity.gameserver.model.Location;
import org.l2junity.gameserver.model.World;
import org.l2junity.gameserver.model.actor.Npc;
import org.l2junity.gameserver.model.actor.instance.PlayerInstance;
import org.l2junity.gameserver.model.holders.SkillHolder;
import org.l2junity.gameserver.network.client.send.string.NpcStringId;

import ai.npc.AbstractNpcAI;

/**
 * Tersi's Herald AI.
 * @author St3eT
 */
public final class TersisHerald extends AbstractNpcAI
{
	// NPCs
	private static final int HERALD = 4326; // Tersi's Herald
	private static final int ANTHARAS = 29068;
	private static final int VALAKAS = 29028;
	private static final int LINDVIOR = 29240;
	// Skills
	private static final SkillHolder DRAGON_BUFF = new SkillHolder(23312, 1); // Fall of the Dragon
	// Location
	private static final Location[] SPAWNS =
	{
		new Location(-13865, 122081, -2984, 32768), // Gludio
		new Location(16200, 142823, -2696, 17736), // Dion
		new Location(83273, 148396, -3400, 0), // Giran
		new Location(82272, 53278, -1488, 16384), // Oren
		new Location(147134, 25925, -2008, 48679), // Aden
		new Location(111620, 219189, -3536, 49152), // Heine
		new Location(148166, -55833, -2776, 53663), // Goddard
		new Location(44153, -48520, -792, 32768), // Rune
		new Location(86971, -142772, -1336, 14324), // Schuttgart
	};
	// Misc
	private static final int DESPAWN_TIME = 4 * 60 * 60 * 1000; // 4h
	private static final NpcStringId[] SPAM_MSGS =
	{
		NpcStringId.SHOW_RESPECT_TO_THE_HEROES_WHO_DEFEATED_THE_EVIL_DRAGON_AND_PROTECTED_THIS_ADEN_WORLD,
		NpcStringId.SHOUT_TO_CELEBRATE_THE_VICTORY_OF_THE_HEROES,
		NpcStringId.PRAISE_THE_ACHIEVEMENT_OF_THE_HEROES_AND_RECEIVE_TERSI_S_BLESSING,
	};
	private static final List<Npc> SPAWNED_NPCS = new ArrayList<>();
	
	private TersisHerald()
	{
		super(TersisHerald.class.getSimpleName(), "ai/npc");
		addStartNpc(HERALD);
		addFirstTalkId(HERALD);
		addTalkId(HERALD);
		addKillId(ANTHARAS, VALAKAS, LINDVIOR);
	}
	
	@Override
	public String onAdvEvent(String event, Npc npc, PlayerInstance player)
	{
		switch (event)
		{
			case "giveBuff":
			{
				if (player.isAffectedBySkill(DRAGON_BUFF.getSkillId()))
				{
					return npc.getId() + "-01.html";
				}
				
				npc.setTarget(player);
				npc.doCast(DRAGON_BUFF.getSkill());
				// DRAGON_BUFF.getSkill().applyEffects(npc, player);
				break;
			}
			case "DESPAWN_NPCS":
			{
				cancelQuestTimer("TEXT_SPAM", null, null);
				SPAWNED_NPCS.stream().forEach(Npc::deleteMe);
				SPAWNED_NPCS.clear();
				break;
			}
			case "TEXT_SPAM":
			{
				SPAWNED_NPCS.stream().forEach(n -> n.broadcastSay(ChatType.NPC_GENERAL, SPAM_MSGS[getRandom(SPAM_MSGS.length)]));
				break;
			}
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onKill(Npc npc, PlayerInstance killer, boolean isSummon)
	{
		final NpcStringId npcStringId;
		switch (npc.getId())
		{
			case ANTHARAS:
				npcStringId = NpcStringId.THE_EVIL_LAND_DRAGON_ANTHARAS_HAS_BEEN_DEFEATED_BY_BRAVE_HEROES;
				break;
			case VALAKAS:
				npcStringId = NpcStringId.THE_EVIL_FIRE_DRAGON_VALAKAS_HAS_BEEN_DEFEATED;
				break;
			case LINDVIOR:
				npcStringId = NpcStringId.HONORABLE_WARRIORS_HAVE_DRIVEN_OFF_LINDVIOR_THE_EVIL_WIND_DRAGON;
				break;
			default:
				return super.onKill(npc, killer, isSummon);
		}
		
		World.getInstance().getPlayers().stream().forEach(p -> showOnScreenMsg(p, npcStringId, 2, 10000, true));
		
		if (!SPAWNED_NPCS.isEmpty())
		{
			cancelQuestTimer("DESPAWN_NPCS", null, null);
			startQuestTimer("DESPAWN_NPCS", DESPAWN_TIME, null, null);
		}
		else
		{
			for (Location loc : SPAWNS)
			{
				SPAWNED_NPCS.add(addSpawn(HERALD, loc));
			}
			
			startQuestTimer("DESPAWN_NPCS", DESPAWN_TIME, null, null);
			startQuestTimer("TEXT_SPAM", 60000, null, null);
		}
		return super.onKill(npc, killer, isSummon);
	}
	
	public static void main(String[] args)
	{
		new TersisHerald();
	}
}