/*
 * Copyright (C) 2004-2015 L2J Unity
 * 
 * This file is part of L2J Unity.
 * 
 * L2J Unity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Unity is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2junity.gameserver.model.stats.finalizers;

import java.util.Optional;

import org.l2junity.Config;
import org.l2junity.gameserver.data.xml.impl.PetDataTable;
import org.l2junity.gameserver.instancemanager.ZoneManager;
import org.l2junity.gameserver.model.PetLevelData;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.actor.instance.PlayerInstance;
import org.l2junity.gameserver.model.actor.transform.TransformTemplate;
import org.l2junity.gameserver.model.stats.BaseStats;
import org.l2junity.gameserver.model.stats.IStatsFunction;
import org.l2junity.gameserver.model.stats.Stats;
import org.l2junity.gameserver.model.zone.ZoneId;
import org.l2junity.gameserver.model.zone.type.SwampZone;

/**
 * @author UnAfraid
 */
public class SpeedFinalizer implements IStatsFunction
{
	@Override
	public double calc(Creature creature, Optional<Double> base, Stats stat)
	{
		throwIfPresent(base);
		
		double baseValue = getBaseSpeed(creature, stat);
		
		final byte speedStat = (byte) creature.getStat().getAdd(Stats.STAT_SPEED, -1);
		if ((speedStat >= 0) && (speedStat < BaseStats.values().length))
		{
			final BaseStats baseStat = BaseStats.values()[speedStat];
			final double bonusDex = Math.max(0, baseStat.calcValue(creature) - 55);
			if (creature.isPlayer())
			{
				System.out.println("speedstat: " + speedStat);
				System.out.println("baseValue: " + baseValue);
				System.out.println(baseStat + " Bonus: " + bonusDex);
			}
			baseValue += bonusDex;
		}
		
		return validateValue(creature, Stats.defaultValue(creature, stat, baseValue), Config.MAX_RUN_SPEED);
	}
	
	private double getBaseSpeed(Creature creature, Stats stat)
	{
		double baseValue = creature.getTemplate().getBaseValue(stat, 0);
		if (creature.isPlayer())
		{
			final PlayerInstance player = creature.getActingPlayer();
			if (player.isTransformed())
			{
				final TransformTemplate template = player.getTransformation().getTemplate(player);
				if (template != null)
				{
					final double speed = template.getStats(stat);
					if (speed > 0)
					{
						baseValue = speed;
					}
				}
			}
			else if (player.isMounted())
			{
				final PetLevelData data = PetDataTable.getInstance().getPetLevelData(player.getMountNpcId(), player.getMountLevel());
				if (data != null)
				{
					baseValue = data.getSpeedOnRide(stat);
					// if level diff with mount >= 10, it decreases move speed by 50%
					if ((player.getMountLevel() - creature.getLevel()) >= 10)
					{
						baseValue /= 2;
					}
					
					// if mount is hungry, it decreases move speed by 50%
					if (player.isHungry())
					{
						baseValue /= 2;
					}
				}
			}
			baseValue += Config.RUN_SPD_BOOST;
		}
		if (creature.isPlayable() && creature.isInsideZone(ZoneId.SWAMP))
		{
			final SwampZone zone = ZoneManager.getInstance().getZone(creature, SwampZone.class);
			if (zone != null)
			{
				baseValue *= zone.getMoveBonus();
			}
		}
		return baseValue;
	}
}
