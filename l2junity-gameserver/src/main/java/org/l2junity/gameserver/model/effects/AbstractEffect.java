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
package org.l2junity.gameserver.model.effects;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.l2junity.Config;
import org.l2junity.gameserver.handler.EffectHandler;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.conditions.Condition;
import org.l2junity.gameserver.model.skills.BuffInfo;
import org.l2junity.gameserver.model.skills.Skill;
import org.l2junity.gameserver.model.stats.functions.AbstractFunction;
import org.l2junity.gameserver.model.stats.functions.FuncTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract effect implementation.<br>
 * Instant effects should not override {@link #onExit(BuffInfo)}.<br>
 * Instant effects should not override {@link #canStart(BuffInfo)}, all checks should be done {@link #onStart(BuffInfo)}.<br>
 * Do not call super class methods {@link #onStart(BuffInfo)} nor {@link #onExit(BuffInfo)}.
 * @author Zoey76
 */
public abstract class AbstractEffect
{
	protected static final Logger _log = LoggerFactory.getLogger(AbstractEffect.class);
	
	// Conditions
	/** Attach condition. */
	private final Condition _attachCond;
	// Apply condition
	// private final Condition _applyCond; // TODO: Use or cleanup.
	private List<FuncTemplate> _funcTemplates;
	/** Effect name. */
	private final String _name;
	/** Ticks. */
	private final int _ticks;
	
	/**
	 * Abstract effect constructor.
	 * @param attachCond the attach condition
	 * @param applyCond the apply condition
	 * @param set the attributes
	 * @param params the parameters
	 */
	protected AbstractEffect(Condition attachCond, Condition applyCond, StatsSet set, StatsSet params)
	{
		_attachCond = attachCond;
		// _applyCond = applyCond;
		_name = set.getString("name");
		_ticks = set.getInt("ticks", 0);
	}
	
	/**
	 * Creates an effect given the parameters.
	 * @param attachCond the attach condition
	 * @param applyCond the apply condition
	 * @param set the attributes
	 * @param params the parameters
	 * @return the new effect
	 */
	public static AbstractEffect createEffect(Condition attachCond, Condition applyCond, StatsSet set, StatsSet params)
	{
		final String name = set.getString("name");
		final Class<? extends AbstractEffect> handler = EffectHandler.getInstance().getHandler(name);
		if (handler == null)
		{
			_log.warn(AbstractEffect.class.getSimpleName() + ": Requested unexistent effect handler: " + name);
			return null;
		}
		
		final Constructor<?> constructor;
		try
		{
			constructor = handler.getConstructor(Condition.class, Condition.class, StatsSet.class, StatsSet.class);
		}
		catch (NoSuchMethodException | SecurityException e)
		{
			_log.warn(AbstractEffect.class.getSimpleName() + ": Requested unexistent constructor for effect handler: " + name + ": " + e.getMessage());
			return null;
		}
		
		try
		{
			return (AbstractEffect) constructor.newInstance(attachCond, applyCond, set, params);
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		{
			_log.warn(AbstractEffect.class.getSimpleName() + ": Unable to initialize effect handler: " + name + ": " + e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * Tests the attach condition.
	 * @param caster the caster
	 * @param target the target
	 * @param skill the skill
	 * @return {@code true} if there isn't a condition to test or it's passed, {@code false} otherwise
	 */
	public boolean testConditions(Creature caster, Creature target, Skill skill)
	{
		return (_attachCond == null) || _attachCond.test(caster, target, skill);
	}
	
	/**
	 * Attaches a function template.
	 * @param f the function
	 */
	public void addFunctionTemplate(FuncTemplate f)
	{
		if (_funcTemplates == null)
		{
			_funcTemplates = new ArrayList<>(1);
		}
		_funcTemplates.add(f);
	}
	
	/**
	 * Gets the effect name.
	 * @return the name
	 */
	public String getName()
	{
		return _name;
	}
	
	/**
	 * Gets the effect ticks
	 * @return the ticks
	 */
	public int getTicks()
	{
		return _ticks;
	}
	
	public double getTicksMultiplier()
	{
		return (getTicks() * Config.EFFECT_TICK_RATIO) / 1000f;
	}
	
	public List<FuncTemplate> getFuncTemplates()
	{
		return _funcTemplates;
	}
	
	/**
	 * Calculates whether this effects land or not.<br>
	 * If it lands will be scheduled and added to the character effect list.<br>
	 * Override in effect implementation to change behavior. <br>
	 * <b>Warning:</b> Must be used only for instant effects continuous effects will not call this they have their success handled by activate_rate.
	 *
	 * @param effector
	 * @param effected
	 *@param skill @return {@code true} if this effect land, {@code false} otherwise
	 */
	public boolean calcSuccess(Creature effector, Creature effected, Skill skill)
	{
		return true;
	}
	
	/**
	 * Get this effect's type.<br>
	 * TODO: Remove.
	 * @return the effect type
	 */
	public L2EffectType getEffectType()
	{
		return L2EffectType.NONE;
	}
	
	/**
	 * Verify if the buff can start.<br>
	 * Used for continuous effects.
	 * @param info the buff info
	 * @return {@code true} if all the start conditions are meet, {@code false} otherwise
	 */
	public boolean canStart(BuffInfo info)
	{
		return true;
	}

	public void instant(Creature effector, Creature effected, Skill skill)
	{

	}

	public void continuousInstant(Creature effector, Creature effected, Skill skill)
	{

	}

	public void onStart(Creature effector, Creature effected, Skill skill)
	{

	}

	/**
	 * Called on effect start.
	 * @param info the buff info
	 */
	public void onStart(BuffInfo info)
	{
		
	}
	
	/**
	 * Called on each tick.<br>
	 * If the abnormal time is lesser than zero it will last forever.
	 * @param info the buff info
	 * @return if {@code true} this effect will continue forever, if {@code false} it will stop after abnormal time has passed
	 */
	public boolean onActionTime(BuffInfo info)
	{
		return false;
	}
	
	/**
	 * Called when the effect is exited.
	 * @param info the buff info
	 */
	public void onExit(BuffInfo info)
	{
		
	}
	
	/**
	 * Get this effect's stats functions.
	 * @param caster the caster
	 * @param target the target
	 * @param skill the skill
	 * @return a list of stat functions.
	 */
	public List<AbstractFunction> getStatFuncs(Creature caster, Creature target, Skill skill)
	{
		if (getFuncTemplates() == null)
		{
			return Collections.emptyList();
		}
		
		final List<AbstractFunction> functions = new ArrayList<>(getFuncTemplates().size());
		for (FuncTemplate template : getFuncTemplates())
		{
			final AbstractFunction function = template.getFunc(caster, target, skill, this);
			if (function != null)
			{
				functions.add(function);
			}
		}
		return functions;
	}
	
	/**
	 * Get the effect flags.
	 * @return bit flag for current effect
	 */
	public int getEffectFlags()
	{
		return EffectFlag.NONE.getMask();
	}
	
	@Override
	public String toString()
	{
		return "Effect " + _name;
	}
	
	public boolean checkCondition(Object obj)
	{
		return true;
	}
	
	/**
	 * Verify if this effect is an instant effect.
	 * @return {@code true} if this effect is instant, {@code false} otherwise
	 */
	public boolean isInstant()
	{
		return false;
	}
}