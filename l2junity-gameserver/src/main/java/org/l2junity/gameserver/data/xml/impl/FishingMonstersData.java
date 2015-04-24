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
package org.l2junity.gameserver.data.xml.impl;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.l2junity.gameserver.data.xml.IXmlReader;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.fishing.L2FishingMonster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * This class holds the Fishing Monsters information.
 * @author nonom
 */
public final class FishingMonstersData implements IXmlReader
{
	private static final Logger LOGGER = LoggerFactory.getLogger(FishingMonstersData.class);
	
	private final Map<Integer, L2FishingMonster> _fishingMonstersData = new HashMap<>();
	
	/**
	 * Instantiates a new fishing monsters data.
	 */
	protected FishingMonstersData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_fishingMonstersData.clear();
		parseDatapackFile("data/stats/fishing/fishingMonsters.xml");
		LOGGER.info("Loaded {} Fishing Monsters.", _fishingMonstersData.size());
	}
	
	@Override
	public void parseDocument(Document doc, File f)
	{
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("fishingMonster".equalsIgnoreCase(d.getNodeName()))
					{
						
						final NamedNodeMap attrs = d.getAttributes();
						final StatsSet set = new StatsSet();
						for (int i = 0; i < attrs.getLength(); i++)
						{
							final Node att = attrs.item(i);
							set.set(att.getNodeName(), att.getNodeValue());
						}
						
						final L2FishingMonster fishingMonster = new L2FishingMonster(set);
						_fishingMonstersData.put(fishingMonster.getFishingMonsterId(), fishingMonster);
					}
				}
			}
		}
	}
	
	/**
	 * Gets the fishing monster.
	 * @param lvl the fisherman level
	 * @return a fishing monster given the fisherman level
	 */
	public L2FishingMonster getFishingMonster(int lvl)
	{
		for (L2FishingMonster fishingMonster : _fishingMonstersData.values())
		{
			if ((lvl >= fishingMonster.getUserMinLevel()) && (lvl <= fishingMonster.getUserMaxLevel()))
			{
				return fishingMonster;
			}
		}
		return null;
	}
	
	/**
	 * Gets the fishing monster by Id.
	 * @param id the fishing monster Id
	 * @return the fishing monster by Id
	 */
	public L2FishingMonster getFishingMonsterById(int id)
	{
		if (_fishingMonstersData.containsKey(id))
		{
			return _fishingMonstersData.get(id);
		}
		return null;
	}
	
	/**
	 * Gets the single instance of FishingMonsterData.
	 * @return single instance of FishingMonsterData
	 */
	public static FishingMonstersData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final FishingMonstersData _instance = new FishingMonstersData();
	}
}
