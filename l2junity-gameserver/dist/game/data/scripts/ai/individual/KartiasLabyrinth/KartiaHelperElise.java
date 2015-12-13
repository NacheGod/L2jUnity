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
package ai.individual.KartiasLabyrinth;

import org.l2junity.gameserver.model.Location;
import org.l2junity.gameserver.model.events.impl.instance.OnInstanceStatusChange;
import org.l2junity.gameserver.model.instancezone.Instance;

import ai.AbstractNpcAI;

/**
 * Kartia Helper Elise AI.
 * @author St3eT
 */
public final class KartiaHelperElise extends AbstractNpcAI
{
	// NPCs
	private static final int[] KARTIA_ELISE =
	{
		33617, // Elise (Kartia 85)
		33628, // Elise (Kartia 90)
		33639, // Elise (Kartia 95)
	};
	private static final int[] KARTIA_SOLO_INSTANCES =
	{
		205, // Solo 85
		206, // Solo 90
		207, // Solo 95
	};
	
	private KartiaHelperElise()
	{
		setInstanceStatusChangeId(this::onInstanceStatusChange, KARTIA_SOLO_INSTANCES);
	}
	
	public void onInstanceStatusChange(OnInstanceStatusChange event)
	{
		final Instance instance = event.getWorld();
		final int status = event.getStatus();
		switch (status)
		{
			case 1:
			{
				// Nothing for now
				break;
			}
			case 2:
			case 3:
			{
				final Location loc = instance.getTemplateParameters().getLocation("eliseTeleportStatus" + status);
				if (loc != null)
				{
					instance.getAliveNpcs(KARTIA_ELISE).forEach(elise -> elise.teleToLocation(loc));
				}
				break;
			}
		}
	}
	
	public static void main(String[] args)
	{
		new KartiaHelperElise();
	}
}