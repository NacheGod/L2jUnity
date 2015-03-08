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
package org.l2junity.gameserver.instancemanager;

import org.l2junity.gameserver.model.entity.Castle;
import org.l2junity.gameserver.model.zone.ZoneType;
import org.l2junity.gameserver.model.zone.type.TownZone;

public final class TownManager
{
	public static final int getTownCastle(int townId)
	{
		switch (townId)
		{
			case 912:
				return 1;
			case 916:
				return 2;
			case 918:
				return 3;
			case 922:
				return 4;
			case 924:
				return 5;
			case 926:
				return 6;
			case 1538:
				return 7;
			case 1537:
				return 8;
			case 1714:
				return 9;
			default:
				return 0;
		}
	}
	
	public static final boolean townHasCastleInSiege(int townId)
	{
		final int castleId = getTownCastle(townId);
		if (castleId > 0)
		{
			Castle castle = CastleManager.getInstance().getCastleById(castleId);
			if (castle != null)
			{
				return castle.getSiege().isInProgress();
			}
		}
		return false;
	}
	
	public static final boolean townHasCastleInSiege(int x, int y)
	{
		return townHasCastleInSiege(MapRegionManager.getInstance().getMapRegionLocId(x, y));
	}
	
	public static final TownZone getTown(int townId)
	{
		for (TownZone temp : ZoneManager.getInstance().getAllZones(TownZone.class))
		{
			if (temp.getTownId() == townId)
			{
				return temp;
			}
		}
		return null;
	}
	
	/**
	 * Returns the town at that position (if any)
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public static final TownZone getTown(int x, int y, int z)
	{
		for (ZoneType temp : ZoneManager.getInstance().getZones(x, y, z))
		{
			if (temp instanceof TownZone)
			{
				return (TownZone) temp;
			}
		}
		return null;
	}
}
