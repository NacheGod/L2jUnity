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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.l2junity.Config;
import org.l2junity.gameserver.data.xml.IGameXmlReader;
import org.l2junity.gameserver.datatables.SkillData;
import org.l2junity.gameserver.enums.CategoryType;
import org.l2junity.gameserver.enums.Race;
import org.l2junity.gameserver.enums.SubclassType;
import org.l2junity.gameserver.model.L2Clan;
import org.l2junity.gameserver.model.SkillLearn;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.actor.instance.PlayerInstance;
import org.l2junity.gameserver.model.base.AcquireSkillType;
import org.l2junity.gameserver.model.base.ClassId;
import org.l2junity.gameserver.model.base.SocialClass;
import org.l2junity.gameserver.model.holders.ItemHolder;
import org.l2junity.gameserver.model.holders.PlayerSkillHolder;
import org.l2junity.gameserver.model.holders.SkillHolder;
import org.l2junity.gameserver.model.interfaces.ISkillsHolder;
import org.l2junity.gameserver.model.skills.CommonSkill;
import org.l2junity.gameserver.model.skills.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * This class loads and manage the characters and pledges skills trees.<br>
 * Here can be found the following skill trees:<br>
 * <ul>
 * <li>Class skill trees: player skill trees for each class.</li>
 * <li>Transfer skill trees: player skill trees for each healer class.</lI>
 * <li>Collect skill tree: player skill tree for Gracia related skills.</li>
 * <li>Fishing skill tree: player skill tree for fishing related skills.</li>
 * <li>Transform skill tree: player skill tree for transformation related skills.</li>
 * <li>Sub-Class skill tree: player skill tree for sub-class related skills.</li>
 * <li>Noble skill tree: player skill tree for noblesse related skills.</li>
 * <li>Hero skill tree: player skill tree for heroes related skills.</li>
 * <li>GM skill tree: player skill tree for Game Master related skills.</li>
 * <li>Common skill tree: custom skill tree for players, skills in this skill tree will be available for all players.</li>
 * <li>Pledge skill tree: clan skill tree for main clan.</li>
 * <li>Sub-Pledge skill tree: clan skill tree for sub-clans.</li>
 * </ul>
 * For easy customization of player class skill trees, the parent Id of each class is taken from the XML data, this means you can use a different class parent Id than in the normal game play, for example all 3rd class dagger users will have Treasure Hunter skills as 1st and 2nd class skills.<br>
 * For XML schema please refer to skillTrees.xsd in datapack in xsd folder and for parameters documentation refer to documentation.txt in skillTrees folder.<br>
 * @author Zoey76
 */
public final class SkillTreesData implements IGameXmlReader
{
	private static final Logger LOGGER = LoggerFactory.getLogger(SkillTreesData.class);
	
	// ClassId, Map of Skill Hash Code, L2SkillLearn
	private static final Map<ClassId, Map<Integer, SkillLearn>> _classSkillTrees = new HashMap<>();
	private static final Map<ClassId, Map<Integer, SkillLearn>> _transferSkillTrees = new HashMap<>();
	private static final Map<Race, Map<Integer, SkillLearn>> _raceSkillTree = new HashMap<>();
	private static final Map<SubclassType, Map<Integer, SkillLearn>> _revelationSkillTree = new HashMap<>();
	private static final Map<ClassId, Set<Integer>> _awakeningSaveSkillTree = new HashMap<>();
	// Skill Hash Code, L2SkillLearn
	private static final Map<Integer, SkillLearn> _collectSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _fishingSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _pledgeSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _subClassSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _subPledgeSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _transformSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _commonSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _subClassChangeSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _abilitySkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _alchemySkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _dualClassSkillTree = new HashMap<>();
	// Other skill trees
	private static final Map<Integer, SkillLearn> _nobleSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _heroSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _gameMasterSkillTree = new HashMap<>();
	private static final Map<Integer, SkillLearn> _gameMasterAuraSkillTree = new HashMap<>();
	// Remove skill tree
	private static final Map<ClassId, Set<Integer>> _removeSkillCache = new HashMap<>();
	
	// Checker, sorted arrays of hash codes
	private Map<Integer, int[]> _skillsByClassIdHashCodes; // Occupation skills
	private Map<Integer, int[]> _skillsByRaceHashCodes; // Race-specific Transformations
	private int[] _allSkillsHashCodes; // Fishing, Collection, Transformations, Common Skills.
	
	private boolean _loading = true;
	
	/** Parent class Ids are read from XML and stored in this map, to allow easy customization. */
	private static final Map<ClassId, ClassId> _parentClassMap = new HashMap<>();
	
	/**
	 * Instantiates a new skill trees data.
	 */
	protected SkillTreesData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_loading = true;
		_classSkillTrees.clear();
		_collectSkillTree.clear();
		_fishingSkillTree.clear();
		_pledgeSkillTree.clear();
		_subClassSkillTree.clear();
		_subPledgeSkillTree.clear();
		_transferSkillTrees.clear();
		_transformSkillTree.clear();
		_nobleSkillTree.clear();
		_subClassChangeSkillTree.clear();
		_abilitySkillTree.clear();
		_alchemySkillTree.clear();
		_heroSkillTree.clear();
		_gameMasterSkillTree.clear();
		_gameMasterAuraSkillTree.clear();
		_raceSkillTree.clear();
		_revelationSkillTree.clear();
		_dualClassSkillTree.clear();
		_removeSkillCache.clear();
		_awakeningSaveSkillTree.clear();
		
		// Load files.
		parseDatapackDirectory("data/skillTrees/", false);
		
		// Generate check arrays.
		generateCheckArrays();
		
		_loading = false;
		
		// Logs a report with skill trees info.
		report();
	}
	
	/**
	 * Parse a skill tree file and store it into the correct skill tree.
	 */
	@Override
	public void parseDocument(Document doc, File f)
	{
		NamedNodeMap attrs;
		Node attr;
		String type = null;
		Race race = null;
		SubclassType subType = null;
		int cId = -1;
		int parentClassId = -1;
		ClassId classId = null;
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("skillTree".equalsIgnoreCase(d.getNodeName()))
					{
						final Map<Integer, SkillLearn> classSkillTree = new HashMap<>();
						final Map<Integer, SkillLearn> transferSkillTree = new HashMap<>();
						final Map<Integer, SkillLearn> raceSkillTree = new HashMap<>();
						final Map<Integer, SkillLearn> revelationSkillTree = new HashMap<>();
						
						type = d.getAttributes().getNamedItem("type").getNodeValue();
						attr = d.getAttributes().getNamedItem("classId");
						if (attr != null)
						{
							cId = Integer.parseInt(attr.getNodeValue());
							classId = ClassId.values()[cId];
						}
						else
						{
							cId = -1;
						}
						
						attr = d.getAttributes().getNamedItem("race");
						if (attr != null)
						{
							race = parseEnum(attr, Race.class);
						}
						
						attr = d.getAttributes().getNamedItem("subType");
						if (attr != null)
						{
							subType = parseEnum(attr, SubclassType.class);
						}
						
						attr = d.getAttributes().getNamedItem("parentClassId");
						if (attr != null)
						{
							parentClassId = Integer.parseInt(attr.getNodeValue());
							if ((cId > -1) && (cId != parentClassId) && (parentClassId > -1) && !_parentClassMap.containsKey(classId))
							{
								_parentClassMap.put(classId, ClassId.values()[parentClassId]);
							}
						}
						
						for (Node c = d.getFirstChild(); c != null; c = c.getNextSibling())
						{
							if ("skill".equalsIgnoreCase(c.getNodeName()))
							{
								final StatsSet learnSkillSet = new StatsSet();
								attrs = c.getAttributes();
								for (int i = 0; i < attrs.getLength(); i++)
								{
									attr = attrs.item(i);
									learnSkillSet.set(attr.getNodeName(), attr.getNodeValue());
								}
								
								final SkillLearn skillLearn = new SkillLearn(learnSkillSet);
								for (Node b = c.getFirstChild(); b != null; b = b.getNextSibling())
								{
									attrs = b.getAttributes();
									switch (b.getNodeName())
									{
										case "item":
											skillLearn.addRequiredItem(new ItemHolder(parseInteger(attrs, "id"), parseInteger(attrs, "count")));
											break;
										case "preRequisiteSkill":
											skillLearn.addPreReqSkill(new SkillHolder(parseInteger(attrs, "id"), parseInteger(attrs, "lvl")));
											break;
										case "race":
											skillLearn.addRace(Race.valueOf(b.getTextContent()));
											break;
										case "residenceId":
											skillLearn.addResidenceId(Integer.valueOf(b.getTextContent()));
											break;
										case "socialClass":
											skillLearn.setSocialClass(Enum.valueOf(SocialClass.class, b.getTextContent()));
											break;
										case "removeSkill":
											final int removeSkillId = parseInteger(attrs, "id");
											skillLearn.addRemoveSkills(removeSkillId);
											_removeSkillCache.computeIfAbsent(classId, k -> new HashSet<>()).add(removeSkillId);
											break;
									}
								}
								
								final int skillHashCode = SkillData.getSkillHashCode(skillLearn.getSkillId(), skillLearn.getSkillLevel());
								switch (type)
								{
									case "classSkillTree":
									{
										if (cId != -1)
										{
											classSkillTree.put(skillHashCode, skillLearn);
										}
										else
										{
											_commonSkillTree.put(skillHashCode, skillLearn);
										}
										break;
									}
									case "transferSkillTree":
									{
										transferSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "collectSkillTree":
									{
										_collectSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "raceSkillTree":
									{
										raceSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "revelationSkillTree":
									{
										revelationSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "fishingSkillTree":
									{
										_fishingSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "pledgeSkillTree":
									{
										_pledgeSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "subClassSkillTree":
									{
										_subClassSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "subPledgeSkillTree":
									{
										_subPledgeSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "transformSkillTree":
									{
										_transformSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "nobleSkillTree":
									{
										_nobleSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "abilitySkillTree":
									{
										_abilitySkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "alchemySkillTree":
									{
										_alchemySkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "heroSkillTree":
									{
										_heroSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "gameMasterSkillTree":
									{
										_gameMasterSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "gameMasterAuraSkillTree":
									{
										_gameMasterAuraSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "subClassChangeSkillTree":
									{
										_subClassChangeSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "dualClassSkillTree":
									{
										_dualClassSkillTree.put(skillHashCode, skillLearn);
										break;
									}
									case "awakeningSaveSkillTree":
									{
										_awakeningSaveSkillTree.computeIfAbsent(classId, k -> new HashSet<>()).add(skillLearn.getSkillId());
										break;
									}
									default:
									{
										LOGGER.warn("Unknown Skill Tree type: " + type + "!");
									}
								}
							}
						}
						
						if (type.equals("transferSkillTree"))
						{
							_transferSkillTrees.put(classId, transferSkillTree);
						}
						else if (type.equals("classSkillTree") && (cId > -1))
						{
							final Map<Integer, SkillLearn> classSkillTrees = _classSkillTrees.get(classId);
							if (classSkillTrees == null)
							{
								_classSkillTrees.put(classId, classSkillTree);
							}
							else
							{
								classSkillTrees.putAll(classSkillTree);
							}
						}
						else if (type.equals("raceSkillTree") && (race != null))
						{
							final Map<Integer, SkillLearn> raceSkillTrees = _raceSkillTree.get(race);
							if (raceSkillTrees == null)
							{
								_raceSkillTree.put(race, raceSkillTree);
							}
							else
							{
								raceSkillTrees.putAll(raceSkillTree);
							}
						}
						else if (type.equals("revelationSkillTree") && (subType != null))
						{
							final Map<Integer, SkillLearn> revelationSkillTrees = _revelationSkillTree.get(race);
							if (revelationSkillTrees == null)
							{
								_revelationSkillTree.put(subType, revelationSkillTree);
							}
							else
							{
								revelationSkillTrees.putAll(revelationSkillTree);
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Method to get the complete skill tree for a given class id.<br>
	 * Include all skills common to all classes.<br>
	 * Includes all parent skill trees.
	 * @param classId the class skill tree Id
	 * @return the complete Class Skill Tree including skill trees from parent class for a given {@code classId}
	 */
	public Map<Integer, SkillLearn> getCompleteClassSkillTree(ClassId classId)
	{
		final Map<Integer, SkillLearn> skillTree = new HashMap<>();
		// Add all skills that belong to all classes.
		skillTree.putAll(_commonSkillTree);
		while ((classId != null) && (_classSkillTrees.get(classId) != null))
		{
			skillTree.putAll(_classSkillTrees.get(classId));
			classId = _parentClassMap.get(classId);
		}
		return skillTree;
	}
	
	/**
	 * Gets the transfer skill tree.<br>
	 * If new classes are implemented over 3rd class, we use a recursive call.
	 * @param classId the transfer skill tree Id
	 * @return the complete Transfer Skill Tree for a given {@code classId}
	 */
	public Map<Integer, SkillLearn> getTransferSkillTree(ClassId classId)
	{
		return _transferSkillTrees.get(classId);
	}
	
	/**
	 * Gets the race skill tree.<br>
	 * @param race the race skill tree Id
	 * @return the complete race Skill Tree for a given {@code Race}
	 */
	public Collection<SkillLearn> getRaceSkillTree(Race race)
	{
		return _raceSkillTree.containsKey(race) ? _raceSkillTree.get(race).values() : Collections.emptyList();
	}
	
	/**
	 * Gets the common skill tree.
	 * @return the complete Common Skill Tree
	 */
	public Map<Integer, SkillLearn> getCommonSkillTree()
	{
		return _commonSkillTree;
	}
	
	/**
	 * Gets the collect skill tree.
	 * @return the complete Collect Skill Tree
	 */
	public Map<Integer, SkillLearn> getCollectSkillTree()
	{
		return _collectSkillTree;
	}
	
	/**
	 * Gets the fishing skill tree.
	 * @return the complete Fishing Skill Tree
	 */
	public Map<Integer, SkillLearn> getFishingSkillTree()
	{
		return _fishingSkillTree;
	}
	
	/**
	 * Gets the pledge skill tree.
	 * @return the complete Pledge Skill Tree
	 */
	public Map<Integer, SkillLearn> getPledgeSkillTree()
	{
		return _pledgeSkillTree;
	}
	
	/**
	 * Gets the sub class skill tree.
	 * @return the complete Sub-Class Skill Tree
	 */
	public Map<Integer, SkillLearn> getSubClassSkillTree()
	{
		return _subClassSkillTree;
	}
	
	/**
	 * Gets the sub class change skill tree.
	 * @return the complete Common Skill Tree
	 */
	public Map<Integer, SkillLearn> getSubClassChangeSkillTree()
	{
		return _subClassChangeSkillTree;
	}
	
	/**
	 * Gets the sub pledge skill tree.
	 * @return the complete Sub-Pledge Skill Tree
	 */
	public Map<Integer, SkillLearn> getSubPledgeSkillTree()
	{
		return _subPledgeSkillTree;
	}
	
	/**
	 * Gets the transform skill tree.
	 * @return the complete Transform Skill Tree
	 */
	public Map<Integer, SkillLearn> getTransformSkillTree()
	{
		return _transformSkillTree;
	}
	
	/**
	 * Gets the ability skill tree.
	 * @return the complete Ability Skill Tree
	 */
	public Map<Integer, SkillLearn> getAbilitySkillTree()
	{
		return _abilitySkillTree;
	}
	
	/**
	 * Gets the ability skill tree.
	 * @return the complete Ability Skill Tree
	 */
	public Map<Integer, SkillLearn> getAlchemySkillTree()
	{
		return _alchemySkillTree;
	}
	
	/**
	 * Gets the noble skill tree.
	 * @return the complete Noble Skill Tree
	 */
	public Map<Integer, Skill> getNobleSkillTree()
	{
		final Map<Integer, Skill> tree = new HashMap<>();
		final SkillData st = SkillData.getInstance();
		for (Entry<Integer, SkillLearn> e : _nobleSkillTree.entrySet())
		{
			tree.put(e.getKey(), st.getSkill(e.getValue().getSkillId(), e.getValue().getSkillLevel()));
		}
		return tree;
	}
	
	/**
	 * Gets the hero skill tree.
	 * @return the complete Hero Skill Tree
	 */
	public Map<Integer, Skill> getHeroSkillTree()
	{
		final Map<Integer, Skill> tree = new HashMap<>();
		final SkillData st = SkillData.getInstance();
		for (Entry<Integer, SkillLearn> e : _heroSkillTree.entrySet())
		{
			tree.put(e.getKey(), st.getSkill(e.getValue().getSkillId(), e.getValue().getSkillLevel()));
		}
		return tree;
	}
	
	/**
	 * Gets the Game Master skill tree.
	 * @return the complete Game Master Skill Tree
	 */
	public Map<Integer, Skill> getGMSkillTree()
	{
		final Map<Integer, Skill> tree = new HashMap<>();
		final SkillData st = SkillData.getInstance();
		for (Entry<Integer, SkillLearn> e : _gameMasterSkillTree.entrySet())
		{
			tree.put(e.getKey(), st.getSkill(e.getValue().getSkillId(), e.getValue().getSkillLevel()));
		}
		return tree;
	}
	
	/**
	 * Gets the Game Master Aura skill tree.
	 * @return the complete Game Master Aura Skill Tree
	 */
	public Map<Integer, Skill> getGMAuraSkillTree()
	{
		final Map<Integer, Skill> tree = new HashMap<>();
		final SkillData st = SkillData.getInstance();
		for (Entry<Integer, SkillLearn> e : _gameMasterAuraSkillTree.entrySet())
		{
			tree.put(e.getKey(), st.getSkill(e.getValue().getSkillId(), e.getValue().getSkillLevel()));
		}
		return tree;
	}
	
	/**
	 * @param player
	 * @param classId
	 * @return {@code true} if player is able to learn new skills on his current level, {@code false} otherwise.
	 */
	public boolean hasAvailableSkills(PlayerInstance player, ClassId classId)
	{
		final Map<Integer, SkillLearn> skills = getCompleteClassSkillTree(classId);
		for (SkillLearn skill : skills.values())
		{
			if ((skill.getSkillId() == CommonSkill.DIVINE_INSPIRATION.getId()) || skill.isAutoGet() || skill.isLearnedByFS() || (skill.getGetLevel() > player.getLevel()))
			{
				continue;
			}
			final Skill oldSkill = player.getKnownSkill(skill.getSkillId());
			if ((oldSkill != null) && (oldSkill.getLevel() == (skill.getSkillLevel() - 1)))
			{
				return true;
			}
			else if ((oldSkill == null) && (skill.getSkillLevel() == 1))
			{
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Gets the available skills.
	 * @param player the learning skill player
	 * @param classId the learning skill class Id
	 * @param includeByFs if {@code true} skills from Forgotten Scroll will be included
	 * @param includeAutoGet if {@code true} Auto-Get skills will be included
	 * @return all available skills for a given {@code player}, {@code classId}, {@code includeByFs} and {@code includeAutoGet}
	 */
	public List<SkillLearn> getAvailableSkills(PlayerInstance player, ClassId classId, boolean includeByFs, boolean includeAutoGet)
	{
		return getAvailableSkills(player, classId, includeByFs, includeAutoGet, player);
	}
	
	/**
	 * Gets the available skills.
	 * @param player the learning skill player
	 * @param classId the learning skill class Id
	 * @param includeByFs if {@code true} skills from Forgotten Scroll will be included
	 * @param includeAutoGet if {@code true} Auto-Get skills will be included
	 * @param holder
	 * @return all available skills for a given {@code player}, {@code classId}, {@code includeByFs} and {@code includeAutoGet}
	 */
	private List<SkillLearn> getAvailableSkills(PlayerInstance player, ClassId classId, boolean includeByFs, boolean includeAutoGet, ISkillsHolder holder)
	{
		final List<SkillLearn> result = new LinkedList<>();
		final Map<Integer, SkillLearn> skills = getCompleteClassSkillTree(classId);
		
		if (skills.isEmpty())
		{
			// The Skill Tree for this class is undefined.
			LOGGER.warn("Skilltree for class " + classId + " is not defined!");
			return result;
		}
		
		final boolean isAwaken = player.isInCategory(CategoryType.AWAKEN_GROUP);
		
		for (Entry<Integer, SkillLearn> entry : skills.entrySet())
		{
			final SkillLearn skill = entry.getValue();
			
			if (((skill.getSkillId() == CommonSkill.DIVINE_INSPIRATION.getId()) && (!Config.AUTO_LEARN_DIVINE_INSPIRATION && includeAutoGet) && !player.isGM()) || (!includeAutoGet && skill.isAutoGet()) || (!includeByFs && skill.isLearnedByFS()) || isRemoveSkill(classId, skill.getSkillId()))
			{
				continue;
			}
			
			if (isAwaken && !isCurrentClassSkillNoParent(classId, entry.getKey()))
			{
				continue;
			}
			
			if (player.getLevel() >= skill.getGetLevel())
			{
				final Skill oldSkill = holder.getKnownSkill(skill.getSkillId());
				if (oldSkill != null)
				{
					if (oldSkill.getLevel() == (skill.getSkillLevel() - 1))
					{
						result.add(skill);
					}
				}
				else if (skill.getSkillLevel() == 1)
				{
					result.add(skill);
				}
			}
		}
		return result;
	}
	
	public Collection<Skill> getAllAvailableSkills(PlayerInstance player, ClassId classId, boolean includeByFs, boolean includeAutoGet)
	{
		// Get available skills
		PlayerSkillHolder holder = new PlayerSkillHolder(player);
		List<SkillLearn> learnable = getAvailableSkills(player, classId, includeByFs, includeAutoGet, holder);
		// while (learnable.size() > 0)
		for (int i = 0; learnable.size() > 0; i++) // Infinite loop warning
		{
			if (i >= 100)
			{
				StringBuilder sb = new StringBuilder();
				for (SkillLearn learn : learnable)
				{
					sb.append(learn.getName()).append(", ");
				}
				LOGGER.error("SkillTreesData infinite loop found. {} learnable skills cannot be learned. Skills: {} ", learnable.size(), sb);
				break;
			}
			for (SkillLearn s : learnable)
			{
				Skill sk = SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel());
				holder.addSkill(sk);
			}
			
			// Get new available skills, some skills depend of previous skills to be available.
			learnable = getAvailableSkills(player, classId, includeByFs, includeAutoGet, holder);
		}
		return holder.getSkills().values();
	}
	
	/**
	 * Gets the available auto get skills.
	 * @param player the player requesting the Auto-Get skills
	 * @return all the available Auto-Get skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableAutoGetSkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		final Map<Integer, SkillLearn> skills = getCompleteClassSkillTree(player.getClassId());
		if (skills.isEmpty())
		{
			// The Skill Tree for this class is undefined, so we return an empty list.
			LOGGER.warn("Skill Tree for this class Id(" + player.getClassId() + ") is not defined!");
			return result;
		}
		
		final Race race = player.getRace();
		final boolean isAwaken = player.isInCategory(CategoryType.AWAKEN_GROUP);
		
		// Race skills
		if (isAwaken)
		{
			for (SkillLearn skill : getRaceSkillTree(race))
			{
				if (player.getKnownSkill(skill.getSkillId()) == null)
				{
					result.add(skill);
				}
			}
		}
		
		for (SkillLearn skill : skills.values())
		{
			if (!skill.getRaces().isEmpty() && !skill.getRaces().contains(race))
			{
				continue;
			}
			
			final int maxLvl = SkillData.getInstance().getMaxLevel(skill.getSkillId());
			final int hashCode = SkillData.getSkillHashCode(skill.getSkillId(), maxLvl);
			
			if (skill.isAutoGet() && (player.getLevel() >= skill.getGetLevel()))
			{
				final Skill oldSkill = player.getKnownSkill(skill.getSkillId());
				if (oldSkill != null)
				{
					if (oldSkill.getLevel() < skill.getSkillLevel())
					{
						result.add(skill);
					}
				}
				else if (!isAwaken || isCurrentClassSkillNoParent(player.getClassId(), hashCode) || isAwakenSaveSkill(player.getClassId(), skill.getSkillId()))
				{
					result.add(skill);
				}
			}
		}
		return result;
	}
	
	/**
	 * Dwarvens will get additional dwarven only fishing skills.
	 * @param player the player
	 * @return all the available Fishing skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableFishingSkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		final Race playerRace = player.getRace();
		for (SkillLearn skill : _fishingSkillTree.values())
		{
			// If skill is Race specific and the player's race isn't allowed, skip it.
			if (!skill.getRaces().isEmpty() && !skill.getRaces().contains(playerRace))
			{
				continue;
			}
			
			if (skill.isLearnedByNpc() && (player.getLevel() >= skill.getGetLevel()))
			{
				final Skill oldSkill = player.getSkills().get(skill.getSkillId());
				if (oldSkill != null)
				{
					if (oldSkill.getLevel() == (skill.getSkillLevel() - 1))
					{
						result.add(skill);
					}
				}
				else if (skill.getSkillLevel() == 1)
				{
					result.add(skill);
				}
			}
		}
		return result;
	}
	
	/**
	 * Gets the available revelation skills
	 * @param player the player requesting the revelation skills
	 * @param type the player current subclass type
	 * @return all the available revelation skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableRevelationSkills(PlayerInstance player, SubclassType type)
	{
		final List<SkillLearn> result = new ArrayList<>();
		Map<Integer, SkillLearn> revelationSkills = _revelationSkillTree.get(type);
		
		for (SkillLearn skill : revelationSkills.values())
		{
			final Skill oldSkill = player.getSkills().get(skill.getSkillId());
			
			if (oldSkill == null)
			{
				result.add(skill);
			}
		}
		return result;
	}
	
	/**
	 * Gets the available alchemy skills, restricted to Ertheia
	 * @param player the player requesting the alchemy skills
	 * @return all the available alchemy skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableAlchemySkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		
		for (SkillLearn skill : _alchemySkillTree.values())
		{
			if (skill.isLearnedByNpc() && (player.getLevel() >= skill.getGetLevel()))
			{
				final Skill oldSkill = player.getSkills().get(skill.getSkillId());
				
				if (oldSkill != null)
				{
					if (oldSkill.getLevel() == (skill.getSkillLevel() - 1))
					{
						result.add(skill);
					}
				}
				else if (skill.getSkillLevel() == 1)
				{
					result.add(skill);
				}
			}
		}
		return result;
	}
	
	/**
	 * Used in Gracia continent.
	 * @param player the collecting skill learning player
	 * @return all the available Collecting skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableCollectSkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		for (SkillLearn skill : _collectSkillTree.values())
		{
			final Skill oldSkill = player.getSkills().get(skill.getSkillId());
			if (oldSkill != null)
			{
				if (oldSkill.getLevel() == (skill.getSkillLevel() - 1))
				{
					result.add(skill);
				}
			}
			else if (skill.getSkillLevel() == 1)
			{
				result.add(skill);
			}
		}
		return result;
	}
	
	/**
	 * Gets the available transfer skills.
	 * @param player the transfer skill learning player
	 * @return all the available Transfer skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableTransferSkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		final ClassId classId = player.getClassId();
		
		if (!_transferSkillTrees.containsKey(classId))
		{
			return result;
		}
		
		for (SkillLearn skill : _transferSkillTrees.get(classId).values())
		{
			// If player doesn't know this transfer skill:
			if (player.getKnownSkill(skill.getSkillId()) == null)
			{
				result.add(skill);
			}
		}
		return result;
	}
	
	/**
	 * Some transformations are not available for some races.
	 * @param player the transformation skill learning player
	 * @return all the available Transformation skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableTransformSkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		final Race race = player.getRace();
		for (SkillLearn skill : _transformSkillTree.values())
		{
			if ((player.getLevel() >= skill.getGetLevel()) && (skill.getRaces().isEmpty() || skill.getRaces().contains(race)))
			{
				final Skill oldSkill = player.getSkills().get(skill.getSkillId());
				if (oldSkill != null)
				{
					if (oldSkill.getLevel() == (skill.getSkillLevel() - 1))
					{
						result.add(skill);
					}
				}
				else if (skill.getSkillLevel() == 1)
				{
					result.add(skill);
				}
			}
		}
		return result;
	}
	
	/**
	 * Gets the available pledge skills.
	 * @param clan the pledge skill learning clan
	 * @return all the available Pledge skills for a given {@code clan}
	 */
	public List<SkillLearn> getAvailablePledgeSkills(L2Clan clan)
	{
		final List<SkillLearn> result = new ArrayList<>();
		
		for (SkillLearn skill : _pledgeSkillTree.values())
		{
			if (!skill.isResidencialSkill() && (clan.getLevel() >= skill.getGetLevel()))
			{
				final Skill oldSkill = clan.getSkills().get(skill.getSkillId());
				if (oldSkill != null)
				{
					if (oldSkill.getLevel() < skill.getSkillLevel())
					{
						result.add(skill);
					}
				}
				else if (skill.getSkillLevel() == 1)
				{
					result.add(skill);
				}
			}
		}
		return result;
	}
	
	/**
	 * Gets the available pledge skills.
	 * @param clan the pledge skill learning clan
	 * @param includeSquad if squad skill will be added too
	 * @return all the available pledge skills for a given {@code clan}
	 */
	public Map<Integer, SkillLearn> getMaxPledgeSkills(L2Clan clan, boolean includeSquad)
	{
		final Map<Integer, SkillLearn> result = new HashMap<>();
		for (SkillLearn skill : _pledgeSkillTree.values())
		{
			if (!skill.isResidencialSkill() && (clan.getLevel() >= skill.getGetLevel()))
			{
				final Skill oldSkill = clan.getSkills().get(skill.getSkillId());
				if ((oldSkill == null) || (oldSkill.getLevel() < skill.getSkillLevel()))
				{
					result.put(skill.getSkillId(), skill);
				}
			}
		}
		
		if (includeSquad)
		{
			for (SkillLearn skill : _subPledgeSkillTree.values())
			{
				if ((clan.getLevel() >= skill.getGetLevel()))
				{
					final Skill oldSkill = clan.getSkills().get(skill.getSkillId());
					if ((oldSkill == null) || (oldSkill.getLevel() < skill.getSkillLevel()))
					{
						result.put(skill.getSkillId(), skill);
					}
				}
			}
		}
		return result;
	}
	
	/**
	 * Gets the available sub pledge skills.
	 * @param clan the sub-pledge skill learning clan
	 * @return all the available Sub-Pledge skills for a given {@code clan}
	 */
	public List<SkillLearn> getAvailableSubPledgeSkills(L2Clan clan)
	{
		final List<SkillLearn> result = new ArrayList<>();
		for (SkillLearn skill : _subPledgeSkillTree.values())
		{
			if ((clan.getLevel() >= skill.getGetLevel()) && clan.isLearnableSubSkill(skill.getSkillId(), skill.getSkillLevel()))
			{
				result.add(skill);
			}
		}
		return result;
	}
	
	/**
	 * Gets the available sub class skills.
	 * @param player the sub-class skill learning player
	 * @return all the available Sub-Class skills for a given {@code player}
	 */
	public List<SkillLearn> getAvailableSubClassSkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		for (SkillLearn skill : _subClassSkillTree.values())
		{
			final Skill oldSkill = player.getSkills().get(skill.getSkillId());
			if (((oldSkill == null) && (skill.getSkillLevel() == 1)) || ((oldSkill != null) && (oldSkill.getLevel() == (skill.getSkillLevel() - 1))))
			{
				result.add(skill);
			}
		}
		return result;
	}
	
	/**
	 * Gets the available dual class skills.
	 * @param player the dual-class skill learning player
	 * @return all the available Dual-Class skills for a given {@code player} sorted by skill ID
	 */
	public List<SkillLearn> getAvailableDualClassSkills(PlayerInstance player)
	{
		final List<SkillLearn> result = new ArrayList<>();
		for (SkillLearn skill : _dualClassSkillTree.values())
		{
			final Skill oldSkill = player.getSkills().get(skill.getSkillId());
			if (((oldSkill == null) && (skill.getSkillLevel() == 1)) || ((oldSkill != null) && (oldSkill.getLevel() == (skill.getSkillLevel() - 1))))
			{
				result.add(skill);
			}
		}
		result.sort(Comparator.comparing(SkillLearn::getSkillId));
		return result;
	}
	
	/**
	 * Gets the available residential skills.
	 * @param residenceId the id of the Castle, Fort, Territory
	 * @return all the available Residential skills for a given {@code residenceId}
	 */
	public List<SkillLearn> getAvailableResidentialSkills(int residenceId)
	{
		final List<SkillLearn> result = new ArrayList<>();
		for (SkillLearn skill : _pledgeSkillTree.values())
		{
			if (skill.isResidencialSkill() && skill.getResidenceIds().contains(residenceId))
			{
				result.add(skill);
			}
		}
		return result;
	}
	
	/**
	 * Just a wrapper for all skill trees.
	 * @param skillType the skill type
	 * @param id the skill Id
	 * @param lvl the skill level
	 * @param player the player learning the skill
	 * @return the skill learn for the specified parameters
	 */
	public SkillLearn getSkillLearn(AcquireSkillType skillType, int id, int lvl, PlayerInstance player)
	{
		SkillLearn sl = null;
		switch (skillType)
		{
			case CLASS:
				sl = getClassSkill(id, lvl, player.getLearningClass());
				break;
			case TRANSFORM:
				sl = getTransformSkill(id, lvl);
				break;
			case FISHING:
				sl = getFishingSkill(id, lvl);
				break;
			case PLEDGE:
				sl = getPledgeSkill(id, lvl);
				break;
			case SUBPLEDGE:
				sl = getSubPledgeSkill(id, lvl);
				break;
			case TRANSFER:
				sl = getTransferSkill(id, lvl, player.getClassId());
				break;
			case SUBCLASS:
				sl = getSubClassSkill(id, lvl);
				break;
			case COLLECT:
				sl = getCollectSkill(id, lvl);
				break;
			case REVELATION:
				sl = getRevelationSkill(SubclassType.BASECLASS, id, lvl);
				break;
			case REVELATION_DUALCLASS:
				sl = getRevelationSkill(SubclassType.DUALCLASS, id, lvl);
				break;
			case ALCHEMY:
				sl = getAlchemySkill(id, lvl);
				break;
			case DUALCLASS:
				sl = getDualClassSkill(id, lvl);
				break;
		}
		return sl;
	}
	
	/**
	 * Gets the transform skill.
	 * @param id the transformation skill Id
	 * @param lvl the transformation skill level
	 * @return the transform skill from the Transform Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getTransformSkill(int id, int lvl)
	{
		return _transformSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the ability skill.
	 * @param id the ability skill Id
	 * @param lvl the ability skill level
	 * @return the ability skill from the Ability Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getAbilitySkill(int id, int lvl)
	{
		return _abilitySkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the alchemy skill.
	 * @param id the alchemy skill Id
	 * @param lvl the alchemy skill level
	 * @return the alchemy skill from the Alchemy Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getAlchemySkill(int id, int lvl)
	{
		return _alchemySkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the class skill.
	 * @param id the class skill Id
	 * @param lvl the class skill level.
	 * @param classId the class skill tree Id
	 * @return the class skill from the Class Skill Trees for a given {@code classId}, {@code id} and {@code lvl}
	 */
	public SkillLearn getClassSkill(int id, int lvl, ClassId classId)
	{
		return getCompleteClassSkillTree(classId).get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the fishing skill.
	 * @param id the fishing skill Id
	 * @param lvl the fishing skill level
	 * @return Fishing skill from the Fishing Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getFishingSkill(int id, int lvl)
	{
		return _fishingSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the pledge skill.
	 * @param id the pledge skill Id
	 * @param lvl the pledge skill level
	 * @return the pledge skill from the Pledge Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getPledgeSkill(int id, int lvl)
	{
		return _pledgeSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the sub pledge skill.
	 * @param id the sub-pledge skill Id
	 * @param lvl the sub-pledge skill level
	 * @return the sub-pledge skill from the Sub-Pledge Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getSubPledgeSkill(int id, int lvl)
	{
		return _subPledgeSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the transfer skill.
	 * @param id the transfer skill Id
	 * @param lvl the transfer skill level.
	 * @param classId the transfer skill tree Id
	 * @return the transfer skill from the Transfer Skill Trees for a given {@code classId}, {@code id} and {@code lvl}
	 */
	public SkillLearn getTransferSkill(int id, int lvl, ClassId classId)
	{
		if (_transferSkillTrees.get(classId) != null)
		{
			return _transferSkillTrees.get(classId).get(SkillData.getSkillHashCode(id, lvl));
		}
		return null;
	}
	
	/**
	 * Gets the race skill.
	 * @param id the race skill Id
	 * @param lvl the race skill level.
	 * @param race the race skill tree Id
	 * @return the transfer skill from the Race Skill Trees for a given {@code race}, {@code id} and {@code lvl}
	 */
	public SkillLearn getRaceSkill(int id, int lvl, Race race)
	{
		for (SkillLearn skill : getRaceSkillTree(race))
		{
			if ((skill.getSkillId() == id) && (skill.getSkillLevel() == lvl))
			{
				return skill;
			}
		}
		return null;
	}
	
	/**
	 * Gets the sub class skill.
	 * @param id the sub-class skill Id
	 * @param lvl the sub-class skill level
	 * @return the sub-class skill from the Sub-Class Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getSubClassSkill(int id, int lvl)
	{
		return _subClassSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the dual class skill.
	 * @param id the dual-class skill Id
	 * @param lvl the dual-class skill level
	 * @return the dual-class skill from the Dual-Class Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getDualClassSkill(int id, int lvl)
	{
		return _dualClassSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the common skill.
	 * @param id the common skill Id.
	 * @param lvl the common skill level
	 * @return the common skill from the Common Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getCommonSkill(int id, int lvl)
	{
		return _commonSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the collect skill.
	 * @param id the collect skill Id
	 * @param lvl the collect skill level
	 * @return the collect skill from the Collect Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getCollectSkill(int id, int lvl)
	{
		return _collectSkillTree.get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the revelation skill.
	 * @param type the subclass type
	 * @param id the revelation skill Id
	 * @param lvl the revelation skill level
	 * @return the revelation skill from the Revelation Skill Tree for a given {@code id} and {@code lvl}
	 */
	public SkillLearn getRevelationSkill(SubclassType type, int id, int lvl)
	{
		return _revelationSkillTree.get(type).get(SkillData.getSkillHashCode(id, lvl));
	}
	
	/**
	 * Gets the minimum level for new skill.
	 * @param player the player that requires the minimum level
	 * @param skillTree the skill tree to search the minimum get level
	 * @return the minimum level for a new skill for a given {@code player} and {@code skillTree}
	 */
	public int getMinLevelForNewSkill(PlayerInstance player, Map<Integer, SkillLearn> skillTree)
	{
		int minLevel = 0;
		if (skillTree.isEmpty())
		{
			LOGGER.warn("SkillTree is not defined for getMinLevelForNewSkill!");
		}
		else
		{
			for (SkillLearn s : skillTree.values())
			{
				if (player.getLevel() < s.getGetLevel())
				{
					if ((minLevel == 0) || (minLevel > s.getGetLevel()))
					{
						minLevel = s.getGetLevel();
					}
				}
			}
		}
		return minLevel;
	}
	
	public List<SkillLearn> getNextAvailableSkills(PlayerInstance player, ClassId classId, boolean includeByFs, boolean includeAutoGet)
	{
		final Map<Integer, SkillLearn> completeClassSkillTree = getCompleteClassSkillTree(classId);
		final List<SkillLearn> result = new LinkedList<>();
		if (completeClassSkillTree.isEmpty())
		{
			return result;
		}
		final int minLevelForNewSkill = getMinLevelForNewSkill(player, completeClassSkillTree);
		
		if (minLevelForNewSkill > 0)
		{
			for (SkillLearn skill : completeClassSkillTree.values())
			{
				if ((!includeAutoGet && skill.isAutoGet()) || (!includeByFs && skill.isLearnedByFS()))
				{
					continue;
				}
				if (minLevelForNewSkill <= skill.getGetLevel())
				{
					final Skill oldSkill = player.getKnownSkill(skill.getSkillId());
					if (oldSkill != null)
					{
						if (oldSkill.getLevel() == (skill.getSkillLevel() - 1))
						{
							result.add(skill);
						}
					}
					else if (skill.getSkillLevel() == 1)
					{
						result.add(skill);
					}
				}
			}
		}
		return result;
	}
	
	public void cleanSkillUponAwakening(PlayerInstance player)
	{
		for (Skill skill : player.getAllSkills())
		{
			
			final int maxLvl = SkillData.getInstance().getMaxLevel(skill.getId());
			final int hashCode = SkillData.getSkillHashCode(skill.getId(), maxLvl);
			
			if (!isCurrentClassSkillNoParent(player.getClassId(), hashCode) && !isRemoveSkill(player.getClassId(), skill.getId()) && !isAwakenSaveSkill(player.getClassId(), skill.getId()))
			{
				player.removeSkill(skill, true, true);
			}
		}
	}
	
	public boolean isAlchemySkill(int skillId, int skillLevel)
	{
		return _alchemySkillTree.containsKey(SkillData.getSkillHashCode(skillId, skillLevel));
	}
	
	/**
	 * Checks if is hero skill.
	 * @param skillId the Id of the skill to check
	 * @param skillLevel the level of the skill to check, if it's -1 only Id will be checked
	 * @return {@code true} if the skill is present in the Hero Skill Tree, {@code false} otherwise
	 */
	public boolean isHeroSkill(int skillId, int skillLevel)
	{
		return _heroSkillTree.containsKey(SkillData.getSkillHashCode(skillId, skillLevel));
	}
	
	/**
	 * Checks if is GM skill.
	 * @param skillId the Id of the skill to check
	 * @param skillLevel the level of the skill to check, if it's -1 only Id will be checked
	 * @return {@code true} if the skill is present in the Game Master Skill Trees, {@code false} otherwise
	 */
	public boolean isGMSkill(int skillId, int skillLevel)
	{
		final int hashCode = SkillData.getSkillHashCode(skillId, skillLevel);
		return _gameMasterSkillTree.containsKey(hashCode) || _gameMasterAuraSkillTree.containsKey(hashCode);
	}
	
	/**
	 * Checks if a skill is a Clan skill.
	 * @param skillId the Id of the skill to check
	 * @param skillLevel the level of the skill to check
	 * @return {@code true} if the skill is present in the Pledge or Subpledge Skill Trees, {@code false} otherwise
	 */
	public boolean isClanSkill(int skillId, int skillLevel)
	{
		final int hashCode = SkillData.getSkillHashCode(skillId, skillLevel);
		return _pledgeSkillTree.containsKey(hashCode) || _subPledgeSkillTree.containsKey(hashCode);
	}
	
	/**
	 * Checks if a skill is a Subclass change skill.
	 * @param skillId the Id of the skill to check
	 * @param skillLevel the level of the skill to check
	 * @return {@code true} if the skill is present in the Subclass change Skill Trees, {@code false} otherwise
	 */
	public boolean isSubClassChangeSkill(int skillId, int skillLevel)
	{
		return _subClassChangeSkillTree.containsKey(SkillData.getSkillHashCode(skillId, skillLevel));
	}
	
	public boolean isRemoveSkill(ClassId classId, int skillId)
	{
		return _removeSkillCache.getOrDefault(classId, Collections.emptySet()).contains(skillId);
	}
	
	public boolean isCurrentClassSkillNoParent(ClassId classId, Integer hashCode)
	{
		return _classSkillTrees.getOrDefault(classId, Collections.emptyMap()).containsKey(hashCode);
	}
	
	public boolean isAwakenSaveSkill(ClassId classId, int skillId)
	{
		return _awakeningSaveSkillTree.getOrDefault(classId, Collections.emptySet()).contains(skillId);
	}
	
	/**
	 * Adds the skills.
	 * @param gmchar the player to add the Game Master skills
	 * @param auraSkills if {@code true} it will add "GM Aura" skills, else will add the "GM regular" skills
	 */
	public void addSkills(PlayerInstance gmchar, boolean auraSkills)
	{
		final Collection<SkillLearn> skills = auraSkills ? _gameMasterAuraSkillTree.values() : _gameMasterSkillTree.values();
		final SkillData st = SkillData.getInstance();
		for (SkillLearn sl : skills)
		{
			gmchar.addSkill(st.getSkill(sl.getSkillId(), sl.getSkillLevel()), false); // Don't Save GM skills to database
		}
	}
	
	/**
	 * Create and store hash values for skills for easy and fast checks.
	 */
	private void generateCheckArrays()
	{
		int i;
		int[] array;
		
		// Class specific skills:
		Map<Integer, SkillLearn> tempMap;
		final Set<ClassId> keySet = _classSkillTrees.keySet();
		_skillsByClassIdHashCodes = new HashMap<>(keySet.size());
		for (ClassId cls : keySet)
		{
			i = 0;
			tempMap = getCompleteClassSkillTree(cls);
			array = new int[tempMap.size()];
			for (int h : tempMap.keySet())
			{
				array[i++] = h;
			}
			tempMap.clear();
			Arrays.sort(array);
			_skillsByClassIdHashCodes.put(cls.ordinal(), array);
		}
		
		// Race specific skills from Fishing and Transformation skill trees.
		final List<Integer> list = new ArrayList<>();
		_skillsByRaceHashCodes = new HashMap<>(Race.values().length);
		for (Race r : Race.values())
		{
			for (SkillLearn s : _fishingSkillTree.values())
			{
				if (s.getRaces().contains(r))
				{
					list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
				}
			}
			
			for (SkillLearn s : _transformSkillTree.values())
			{
				if (s.getRaces().contains(r))
				{
					list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
				}
			}
			
			i = 0;
			array = new int[list.size()];
			for (int s : list)
			{
				array[i++] = s;
			}
			Arrays.sort(array);
			_skillsByRaceHashCodes.put(r.ordinal(), array);
			list.clear();
		}
		
		// Skills available for all classes and races
		for (SkillLearn s : _commonSkillTree.values())
		{
			if (s.getRaces().isEmpty())
			{
				list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
			}
		}
		
		for (SkillLearn s : _fishingSkillTree.values())
		{
			if (s.getRaces().isEmpty())
			{
				list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
			}
		}
		
		for (SkillLearn s : _transformSkillTree.values())
		{
			if (s.getRaces().isEmpty())
			{
				list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
			}
		}
		
		for (SkillLearn s : _collectSkillTree.values())
		{
			list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
		}
		
		for (SkillLearn s : _abilitySkillTree.values())
		{
			list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
		}
		
		for (SkillLearn s : _alchemySkillTree.values())
		{
			list.add(SkillData.getSkillHashCode(s.getSkillId(), s.getSkillLevel()));
		}
		
		_allSkillsHashCodes = new int[list.size()];
		int j = 0;
		for (int hashcode : list)
		{
			_allSkillsHashCodes[j++] = hashcode;
		}
		Arrays.sort(_allSkillsHashCodes);
	}
	
	/**
	 * Verify if the give skill is valid for the given player.<br>
	 * GM's skills are excluded for GM players
	 * @param player the player to verify the skill
	 * @param skill the skill to be verified
	 * @return {@code true} if the skill is allowed to the given player
	 */
	public boolean isSkillAllowed(PlayerInstance player, Skill skill)
	{
		if (skill.isExcludedFromCheck())
		{
			return true;
		}
		
		if (player.isGM() && skill.isGMSkill())
		{
			return true;
		}
		
		// Prevent accidental skill remove during reload
		if (_loading)
		{
			return true;
		}
		
		final int maxLvl = SkillData.getInstance().getMaxLevel(skill.getId());
		final int hashCode = SkillData.getSkillHashCode(skill.getId(), Math.min(skill.getLevel(), maxLvl));
		
		if (Arrays.binarySearch(_skillsByClassIdHashCodes.get(player.getClassId().ordinal()), hashCode) >= 0)
		{
			return true;
		}
		
		if (Arrays.binarySearch(_skillsByRaceHashCodes.get(player.getRace().ordinal()), hashCode) >= 0)
		{
			return true;
		}
		
		if (Arrays.binarySearch(_allSkillsHashCodes, hashCode) >= 0)
		{
			return true;
		}
		
		// Exclude Transfer Skills from this check.
		if (getTransferSkill(skill.getId(), Math.min(skill.getLevel(), maxLvl), player.getClassId()) != null)
		{
			return true;
		}
		
		// Exclude Race skills from this check.
		if (getRaceSkill(skill.getId(), Math.min(skill.getLevel(), maxLvl), player.getRace()) != null)
		{
			return true;
		}
		return false;
	}
	
	/**
	 * Logs current Skill Trees skills count.
	 */
	private void report()
	{
		int classSkillTreeCount = 0;
		for (Map<Integer, SkillLearn> classSkillTree : _classSkillTrees.values())
		{
			classSkillTreeCount += classSkillTree.size();
		}
		
		int transferSkillTreeCount = 0;
		for (Map<Integer, SkillLearn> trasferSkillTree : _transferSkillTrees.values())
		{
			transferSkillTreeCount += trasferSkillTree.size();
		}
		
		int raceSkillTreeCount = 0;
		for (Map<Integer, SkillLearn> raceSkillTree : _raceSkillTree.values())
		{
			raceSkillTreeCount += raceSkillTree.size();
		}
		
		int revelationSkillTreeCount = 0;
		for (Map<Integer, SkillLearn> revelationSkillTree : _revelationSkillTree.values())
		{
			revelationSkillTreeCount += revelationSkillTree.size();
		}
		
		int dwarvenOnlyFishingSkillCount = 0;
		for (SkillLearn fishSkill : _fishingSkillTree.values())
		{
			if (fishSkill.getRaces().contains(Race.DWARF))
			{
				dwarvenOnlyFishingSkillCount++;
			}
		}
		
		int resSkillCount = 0;
		for (SkillLearn pledgeSkill : _pledgeSkillTree.values())
		{
			if (pledgeSkill.isResidencialSkill())
			{
				resSkillCount++;
			}
		}
		
		LOGGER.info("Loaded {} Class Skills for {} Class Skill Trees.", classSkillTreeCount, _classSkillTrees.size());
		LOGGER.info("Loaded {} Sub-Class Skills.", _subClassSkillTree.size());
		LOGGER.info("Loaded {} Dual-Class Skills.", _dualClassSkillTree.size());
		LOGGER.info("Loaded {} Transfer Skills for {} Transfer Skill Trees.", transferSkillTreeCount, _transferSkillTrees.size());
		LOGGER.info("Loaded {} Race skills for {} Race Skill Trees.", raceSkillTreeCount, _raceSkillTree.size());
		LOGGER.info("Loaded {} Fishing Skills, {} Dwarven only Fishing Skills.", _fishingSkillTree.size(), dwarvenOnlyFishingSkillCount);
		LOGGER.info("Loaded {} Collect Skills.", _collectSkillTree.size());
		LOGGER.info("Loaded {} Pledge Skills, {} for Pledge and {} Residential.", _pledgeSkillTree.size(), (_pledgeSkillTree.size() - resSkillCount), resSkillCount);
		LOGGER.info("Loaded {} Sub-Pledge Skills.", _subPledgeSkillTree.size());
		LOGGER.info("Loaded {} Transform Skills.", _transformSkillTree.size());
		LOGGER.info("Loaded {} Noble Skills.", _nobleSkillTree.size());
		LOGGER.info("Loaded {} Hero Skills.", _heroSkillTree.size());
		LOGGER.info("Loaded {} Game Master Skills.", _gameMasterSkillTree.size());
		LOGGER.info("Loaded {} Game Master Aura Skills.", _gameMasterAuraSkillTree.size());
		LOGGER.info("Loaded {} Ability Skills.", _abilitySkillTree.size());
		LOGGER.info("Loaded {} Alchemy Skills.", _alchemySkillTree.size());
		LOGGER.info("Loaded {} Class Awaken Save Skills.", _awakeningSaveSkillTree.size());
		LOGGER.info("Loaded {} Revelation Skills.", revelationSkillTreeCount);
		
		final int commonSkills = _commonSkillTree.size();
		if (commonSkills > 0)
		{
			LOGGER.info("Loaded {} Common Skills to all classes.", commonSkills);
		}
		LOGGER.info("Loaded {} Subclass change Skills.", _subClassChangeSkillTree.size());
	}
	
	/**
	 * Gets the single instance of SkillTreesData.
	 * @return the only instance of this class
	 */
	public static SkillTreesData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/**
	 * Singleton holder for the SkillTreesData class.
	 */
	private static class SingletonHolder
	{
		protected static final SkillTreesData _instance = new SkillTreesData();
	}
}
