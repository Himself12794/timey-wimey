package com.himself12794.notenoughtime.handlers;

import java.lang.reflect.Field;

import com.himself12794.notenoughtime.world.TimeFlowData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class TimeFlowHandler {
	
	private int dayLength = 24000;
	private int nightTime = 12000;
	private boolean getManagerFailed = false;
	private ServerConfigurationManager configManager = null;
	
	public TimeFlowHandler() {
	}
	
	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event) {
		
		if (configManager == null && !getManagerFailed) {
			configManager = getServerConfigManager();
		}
		
		if (event.phase == Phase.START) {
			updateTime(event.world);
		} 
	}

	/**
	 * Run every tick to manually update the time if this mod is enabled, since normal daylight cycle 
	 * has been halted.
	 * 
	 * @param world
	 */
	public void updateTime(World world) {
		long worldTime = world.getWorldInfo().getWorldTime();
		long adjustedTime = worldTime % dayLength;
		boolean isDay = adjustedTime < nightTime;
		
		TimeFlowData data = TimeFlowData.getForWorld(world);
		
		if (data.isEnabled) {
		
			world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
			float multiplier;
			if (isDay) {
				multiplier = data.dayLengthMultiplier;
			} else {
				multiplier = data.nightLengthMultiplier;
			}
			int fullTicks = (int)multiplier;
			double partialTicks = data.partialTickCounter;
			if (Math.abs(partialTicks) >= 1) {
				fullTicks += (int)partialTicks;
				data.partialTickCounter = getPartialValue(partialTicks);
			}
			double partialMult = getPartialValue(multiplier);
			data.partialTickCounter += partialMult;
			worldTime += fullTicks;
			
			// Prevent negative time
			if (worldTime < 0) {
				worldTime %= dayLength;
				worldTime += dayLength;
			}
			world.getWorldInfo().setWorldTime(worldTime);
			if (configManager != null) {
				configManager.sendPacketToAllPlayersInDimension(new S03PacketTimeUpdate(world.getTotalWorldTime(), world.getWorldTime(), data.isEnabled), world.provider.dimensionId);
			}
		} 
		
		data.markDirty();
		
	}
	
	public ServerConfigurationManager getServerConfigManager() {
		
		try {
			Field field = MinecraftServer.class.getDeclaredField("serverConfigManager");
			field.setAccessible(true);
			return (ServerConfigurationManager)field.get(MinecraftServer.getServer());
		} catch (Exception e) {
			getManagerFailed = true;
		}
		return null;
	}
	
	/**
	 * Gets the partial value of a number, which is the value after the decimal.
	 * 
	 * @param value
	 * @return
	 */
	private static double getPartialValue(double value) {
		int head = value < 0 ? MathHelper.ceiling_double_int(value) : MathHelper.floor_double(value);
		return value - head;
	}
	
}