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
package handlers.effecthandlers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.l2junity.commons.util.Rnd;
import org.l2junity.gameserver.enums.ShotType;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.actor.Attackable;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.effects.AbstractEffect;
import org.l2junity.gameserver.model.effects.L2EffectType;
import org.l2junity.gameserver.model.items.instance.ItemInstance;
import org.l2junity.gameserver.model.skills.AbnormalType;
import org.l2junity.gameserver.model.skills.Skill;
import org.l2junity.gameserver.model.stats.BaseStats;
import org.l2junity.gameserver.model.stats.Formulas;
import org.l2junity.gameserver.model.stats.Stats;

/**
 * Physical Attack effect implementation. <br>
 * Current formulas were tested to be the best matching retail, damage appears to be identical: <br>
 * For melee skills: 70 * graciaSkillBonus1.10113 * (patk * lvlmod + power) * crit * ss * skillpowerbonus / pdef <br>
 * For ranged skills: 70 * (patk * lvlmod + power + patk + power) * crit * ss * skillpower / pdef <br>
 * @author Nik
 */
public final class PhysicalAttack extends AbstractEffect
{
	private final double _power;
	private final double _pAtkMod;
	private final double _pDefMod;
	private final double _criticalChance;
	private final boolean _ignoreShieldDefence;
	private final boolean _overHit;
	
	private final Set<AbnormalType> _abnormals;
	private final double _abnormalPowerMod;
	
	public PhysicalAttack(StatsSet params)
	{
		_power = params.getDouble("power", 0);
		_pAtkMod = params.getDouble("pAtkMod", 1.0);
		_pDefMod = params.getDouble("pDefMod", 1.0);
		_criticalChance = params.getDouble("criticalChance", 0);
		_ignoreShieldDefence = params.getBoolean("ignoreShieldDefence", false);
		_overHit = params.getBoolean("overHit", false);
		
		String abnormals = params.getString("abnormalType", null);
		if ((abnormals != null) && !abnormals.isEmpty())
		{
			_abnormals = new HashSet<>();
			for (String slot : abnormals.split(";"))
			{
				_abnormals.add(AbnormalType.getAbnormalType(slot));
			}
		}
		else
		{
			_abnormals = Collections.<AbnormalType> emptySet();
		}
		_abnormalPowerMod = params.getDouble("damageModifier", 1);
	}
	
	@Override
	public boolean calcSuccess(Creature effector, Creature effected, Skill skill)
	{
		return !Formulas.calcPhysicalSkillEvasion(effector, effected, skill);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.PHYSICAL_ATTACK;
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void instant(Creature effector, Creature effected, Skill skill, ItemInstance item)
	{
		if (effector.isAlikeDead())
		{
			return;
		}
		
		if (effected.isPlayer() && effected.getActingPlayer().isFakeDeath())
		{
			effected.stopFakeDeath(true);
		}
		
		if (_overHit && effected.isAttackable())
		{
			((Attackable) effected).overhitEnabled(true);
		}
		
		final double attack = effector.getPAtk() * _pAtkMod;
		double defence = effected.getPDef() * _pDefMod;
		
		if (!_ignoreShieldDefence)
		{
			switch (Formulas.calcShldUse(effector, effected, skill, true))
			{
				case Formulas.SHIELD_DEFENSE_SUCCEED:
				{
					defence += effected.getShldDef();
					break;
				}
				case Formulas.SHIELD_DEFENSE_PERFECT_BLOCK:
				{
					defence = -1;
					break;
				}
			}
		}
		
		double damage = 1;
		final boolean critical = (_criticalChance > 0) && ((BaseStats.STR.calcBonus(effector) * _criticalChance) > (Rnd.nextDouble() * 100));
		
		if (defence != -1)
		{
			// Trait, elements
			final double weaponTraitMod = Formulas.calcWeaponTraitBonus(effector, effected);
			final double generalTraitMod = Formulas.calcGeneralTraitBonus(effector, effected, skill.getTraitType(), false);
			final double attributeMod = Formulas.calcAttributeBonus(effector, effected, skill);
			final double pvpPveMod = Formulas.calculatePvpPveBonus(effector, effected, skill, true);
			final double randomMod = effector.getRandomDamageMultiplier();
			
			// Skill specific mods.
			final double wpnMod = effector.getAttackType().isRanged() ? 70 : (70 * 1.10113);
			final double rangedBonus = effector.getAttackType().isRanged() ? (attack + _power) : 0;
			final double skillPowerMod = effector.getStat().getValue(Stats.PHYSICAL_SKILL_POWER);
			final double abnormalMod = _abnormals.stream().anyMatch(a -> effected.getEffectList().getBuffInfoByAbnormalType(a) != null) ? _abnormalPowerMod : 1;
			final double critMod = critical ? Formulas.calcCritDamage(effector, effected, skill) : 1;
			final double ssmod = (skill.useSoulShot() && effector.isChargedShot(ShotType.SOULSHOTS)) ? effector.getStat().getValue(Stats.SHOTS_BONUS, 2) : 1; // 2.04 for dual weapon?
			
			// ...................____________Melee Damage_____________......................................___________________Ranged Damage____________________
			// ATTACK CALCULATION 77 * ((pAtk * lvlMod) + power) / pdef            RANGED ATTACK CALCULATION 70 * ((pAtk * lvlMod) + power + patk + power) / pdef
			// ```````````````````^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^``````````````````````````````````````^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
			final double baseMod = (wpnMod * ((attack * effector.getLevelMod()) + _power + rangedBonus)) / defence;
			damage = baseMod * skillPowerMod * abnormalMod * ssmod * critMod * weaponTraitMod * generalTraitMod * attributeMod * pvpPveMod * randomMod;
		}
		
		// Check if damage should be reflected
		Formulas.calcDamageReflected(effector, effected, skill, critical);
		final double damageCap = effected.getStat().getValue(Stats.DAMAGE_LIMIT);
		if (damageCap > 0)
		{
			damage = Math.min(damage, damageCap);
		}
		damage = effected.notifyDamageReceived(damage, effector, skill, critical, false, false);
		effected.reduceCurrentHp(damage, effector, skill);
		effector.sendDamageMessage(effected, skill, (int) damage, critical, false);
		
		if (skill.isSuicideAttack())
		{
			effector.doDie(effector);
		}
	}
}
