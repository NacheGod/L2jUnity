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
package handlers.targethandlers.affectscope;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.l2junity.gameserver.handler.AffectObjectHandler;
import org.l2junity.gameserver.handler.IAffectObjectHandler;
import org.l2junity.gameserver.handler.IAffectScopeHandler;
import org.l2junity.gameserver.model.World;
import org.l2junity.gameserver.model.WorldObject;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.skills.Skill;
import org.l2junity.gameserver.model.skills.targets.AffectScope;
import org.l2junity.gameserver.util.Util;

/**
 * Ring Range affect scope implementation. Gathers objects in ring/donut shaped area with start and end range.
 * @author Nik
 */
public class RingRange implements IAffectScopeHandler
{
	@Override
	public List<? extends WorldObject> getAffectedScope(Creature activeChar, Creature target, Skill skill)
	{
		final IAffectObjectHandler affectObject = AffectObjectHandler.getInstance().getHandler(skill.getAffectObject());
		final int affectRange = skill.getAffectRange();
		final int affectLimit = skill.getAffectLimit();
		final int startRange = skill.getFanRange()[2];
		final List<Creature> result = new LinkedList<>();
		
		// Target checks.
		final Predicate<Creature> filter = c ->
		{
			if (c.isDead())
			{
				return false;
			}
			
			// Targets before the start range are unaffected.
			if (Util.checkIfInRange(startRange, activeChar, c, false))
			{
				return false;
			}
			
			return (affectObject == null) || affectObject.checkAffectedObject(activeChar, c);
		};
		
		// Add object of origin since its skipped in the forEachVisibleObjectInRange method.
		if (filter.test(activeChar))
		{
			result.add(activeChar);
		}
		
		// Check and add targets.
		World.getInstance().forEachVisibleObjectInRange(activeChar, Creature.class, affectRange, c ->
		{
			if ((affectLimit > 0) && (result.size() >= affectLimit))
			{
				return;
			}
			if (filter.test(c))
			{
				result.add(c);
			}
		});
		
		return result;
	}
	
	@Override
	public Enum<AffectScope> getAffectScopeType()
	{
		return AffectScope.RING_RANGE;
	}
}
