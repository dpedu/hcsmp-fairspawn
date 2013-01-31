package com.psychobit.fairspawn;

import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.psychobit.campfire.Campfire;
import com.psychobit.campfire.PlayerData;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.trc202.CombatTag.CombatTag;
import com.trc202.CombatTagApi.CombatTagApi;

/**
 * Makes spawn a fair place to play in regards to damage
 * @author psychobit
 *
 */
public class FairSpawn extends JavaPlugin implements Listener
{
	
	/**
	 * Worldguard instance
	 */
	private WorldGuardPlugin _worldguard;
	
	/**
	 * Campfire instance
	 */
	private Campfire _campfire;
	
	/**
	 * CombatTag instance
	 */
	private CombatTag _combattag;
	
	/**
	 * Thread ID's for people tagged 
	 */
	private HashMap<String,Integer> _combat;
	
	/**
	 * Register events and create objects
	 */
	public void onEnable()
	{
		this.getServer().getPluginManager().registerEvents( this, this );
		this._combat = new HashMap<String,Integer>();
		
		// Check for worldguard
		Plugin p = this.getServer().getPluginManager().getPlugin( "WorldGuard" );
		if ( p != null && p instanceof WorldGuardPlugin )
		{
			System.out.println( "[FairSpawn] Found WorldGuard" );
			this._worldguard= ( WorldGuardPlugin ) p;
		}
		
		// Check for campfire
		p = this.getServer().getPluginManager().getPlugin( "CombatTag" );
		if ( p != null && p instanceof CombatTag )
		{
			System.out.println( "[FairSpawn] Found CombatTag" );
			this._combattag = ( CombatTag ) p;
		}
		
		// Check for campfire
		p = this.getServer().getPluginManager().getPlugin( "Campfire" );
		if ( p != null && p instanceof Campfire )
		{
			System.out.println( "[FairSpawn] Found Campfire!" );
			this._campfire = ( Campfire ) p;
		}
	}
	
	/**
	 * Allow people who have been CombatTagged to be hit inside a protected zone
	 * This prevents people from ducking back inside a protected zone to be safe
	 * Once they have been tagged, they must avoid being hit
	 * They also still can't hit people themselves if they are in a protected zone 
	 * @param e
	 */
	@EventHandler( priority=EventPriority.HIGHEST )
	public void onDamageCT( EntityDamageByEntityEvent e )
	{
		// Check if the victim is a player or NPC
		Entity ent = e.getEntity();
		if ( !( ent instanceof Player ) ) return;
		Player victim = ( Player ) ent;
		
		// Check for NPC
		boolean isNPC = false;
		CombatTagApi combatApi;
		if (getServer().getPluginManager().getPlugin("CombatTag") != null) {
			combatApi = new CombatTagApi((CombatTag) getServer()
					.getPluginManager().getPlugin("CombatTag"));
			if (combatApi != null) {
				isNPC = combatApi.isNPC(ent);
			}
		}
		
		// Check if the server is running campfire first
		if ( this._campfire != null && !isNPC )
		{
			PlayerData d = this._campfire.getPlayerData( victim.getName() );
			if ( d != null && d.isEnabled() ) return; // Person being hit is under campfire. Abort
			
			if ( e.getDamager() instanceof Player )
			{
				Player attacker = ( Player ) e.getDamager();
				d = this._campfire.getPlayerData( attacker.getName() );
				if ( d != null && d.isEnabled() ) return; // Person attacking is under campfire. Abort
			}
		}
		
		// Get the attacker
		Player attacker = null;
		// Check for arrow damage
		if (e.getDamager() instanceof Arrow) {
			// Get the arrow's owner
			Arrow arrow = (Arrow) e.getDamager();
			if (!(arrow.getShooter() instanceof Player))
				return;
			attacker = (Player) arrow.getShooter();
		// Check for potion damage
		} else if (e.getDamager() instanceof ThrownPotion) {
			
			ThrownPotion potion = (ThrownPotion) e.getDamager();
			
			if (!(potion.getShooter() instanceof Player)) {
				return;
			}
			attacker = (Player) potion.getShooter();
			
		} else if (!(e.getDamager() instanceof Player)) {
			return;
		} else {
			attacker = (Player) e.getDamager();
		}

		// Check attacker and make sure the server is running WorldGuard
		if ( attacker != null && this._worldguard != null && !isNPC )
		{			
			// Check if the attacker is in NoPvP or Invincible regions
			boolean attackerInNoPvP = !this._worldguard.getRegionManager( attacker.getWorld() ).getApplicableRegions( attacker.getLocation() ).allows( DefaultFlag.PVP );
			boolean attackerInInvincible = this._worldguard.getRegionManager( attacker.getWorld() ).getApplicableRegions( attacker.getLocation() ).allows( DefaultFlag.INVINCIBILITY );
			
			// If the attacker is in a protected region, prevent the damage if they aren't CT'd
			if ( attackerInNoPvP || attackerInInvincible ) e.setCancelled( true );
			
			// If the attacker is not combat tagged, then leave the event cancelled
			if ( !this.isCombatTagged( attacker.getName() ) ) return;
		}
		
		// Check if the victim is Combat Tagged
		if ( !isNPC && !this.isCombatTagged( victim.getName() ) ) return;
		
		// Uncancel the event. Whahahahaha
		e.setCancelled( false );
	}
	
	/**
	 * Checks for health events when combat tagged.
	 * 
	 * @param e
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onHealthCT(EntityRegainHealthEvent e) {
		System.out.println("[Fairspawn] DEBUG: HEALING EVENT");
		Entity ent = e.getEntity();
		if (!(ent instanceof Player)) {
			return;
		}
		Player victim = (Player) ent;
		
		// If player is combat tagged, cancel.
		if (isCombatTagged(victim.getName())) {
			System.out.println("[Fairspawn] DEBUG: That bitch is combat tagged!");
			return;
		}
		
		// If player is not in a a safe zone, cancel.
		if (this._worldguard.getRegionManager(victim.getWorld()).getApplicableRegions(victim.getLocation()).allows(DefaultFlag.PVP)) {
			System.out.println("[Fairspawn] DEBUG: YOU AIN'T IN NO SAFE ZONE.");
			return;
		}
		
		// If item is not a potion, cancel.
		if (!(e.getRegainReason() == RegainReason.MAGIC) && !(e.getRegainReason() == RegainReason.MAGIC_REGEN)  ) {
			System.out.println("[Fairspawn] DEBUG: That isn'tf a 'magic' or 'regen' potion.");
			return;
		}
		
		// Player is not CT'd, and in a safe zone, so cancel healing potions.
		e.setCancelled(true);		
	}
	
	/**
	 * Add a notifier for people who have been CT'd once their CT expires
	 * @param e
	 */
	@EventHandler( priority=EventPriority.HIGHEST )
	public void checkCT( EntityDamageByEntityEvent e )
	{
		if ( !( e.getEntity() instanceof Player ) ) return;
		if ( !( e.getDamager() instanceof Player ) ) return;
		if ( this._combattag == null ) return;
		final Player victim = ( Player ) e.getEntity();
		final Player attacker = ( Player ) e.getDamager();
		final String victimName = victim.getName();
		final String attackerName = attacker.getName();
		final FairSpawn plugin = this;
		this.getServer().getScheduler().scheduleSyncDelayedTask( this, new Runnable()
		{
			@Override
			public void run()
			{
				if ( plugin.isCombatTagged( victimName ) )
				{
					if ( plugin._combat.containsKey( victimName ) ) plugin.getServer().getScheduler().cancelTask( plugin._combat.get( victimName ) );
					int threadID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask( plugin, new Runnable()
					{
						@Override
						public void run()
						{
							Player player = plugin.getServer().getPlayer( victimName );
							if ( player != null && !plugin.isCombatTagged( victimName ) )
							{
								player.sendMessage( ChatColor.RED + "[CombatTag] You are no longer in combat" );
								plugin.getServer().getScheduler().cancelTask( plugin._combat.get( victimName ) );
							}
						}
					}, 20L, 20L );
					plugin._combat.put( victimName, threadID );
				}
				if ( plugin.isCombatTagged( attacker.getName() ) )
				{
					if ( plugin._combat.containsKey( attackerName ) ) plugin.getServer().getScheduler().cancelTask( plugin._combat.get( attackerName ) );
					int threadID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask( plugin, new Runnable()
					{
						@Override
						public void run()
						{
							Player player = plugin.getServer().getPlayer( attackerName );
							if ( player != null && !plugin.isCombatTagged( attackerName ) )
							{
								player.sendMessage( ChatColor.RED + "[CombatTag] You are no longer in combat" );
								plugin.getServer().getScheduler().cancelTask( plugin._combat.get( attackerName ) );
							}
						}
					}, 20L, 20L );
					plugin._combat.put( attackerName, threadID );
				}
			}
			
		} );
		
	}
	
	/**
	 * Mark worldguard, campfire, and combattag as enabled if they are enabled
	 * @param e
	 */
	@EventHandler( priority = EventPriority.LOW )
	public void onPluginLoad( PluginEnableEvent e )
	{
		Plugin p = e.getPlugin();
		if ( p.getDescription().getName().equals( "WorldGuard" ) && p instanceof WorldGuardPlugin )
		{
			System.out.println( "[Fairspawn] Found WorldGuard!" );
			this._worldguard = ( WorldGuardPlugin ) p;
			return;
		}
		if ( p.getDescription().getName().equals( "Campfire" ) && p instanceof Campfire )
		{
			System.out.println( "[Fairspawn] Found Campfire!" );
			this._campfire = ( Campfire ) p;
			return;
		}
		if ( p.getDescription().getName().equals( "CombatTag" ) && p instanceof CombatTag )
		{
			System.out.println( "[Fairspawn] Found CombatTag!" );
			this._combattag = ( CombatTag ) p;
			return;
		}
	}
	
	/**
	 * Mark worldguard, campfire, and combattag as disabled if they are disabled
	 * @param e
	 */
	@EventHandler( priority = EventPriority.LOW )
	public void onPluginLoad( PluginDisableEvent e )
	{
		Plugin p = e.getPlugin();
		if ( p.getDescription().getName().equals( "WorldGuard" ) && p instanceof WorldGuardPlugin )
		{
			System.out.println( "[Fairspawn] WorldGuard disabled!" );
			this._worldguard = null;
			return;
		}
		if ( p.getDescription().getName().equals( "Campfire" ) && p instanceof Campfire )
		{
			System.out.println( "[Fairspawn] Campfire disabled!" );
			this._campfire = null;
			return;
		}
		if ( p.getDescription().getName().equals( "CombatTag" ) && p instanceof CombatTag )
		{
			System.out.println( "[Fairspawn] CombatTag disabled!" );
			this._combattag = null;
			return;
		}
	}
	
	
	/**
	 * Check if a player is in combat tag
	 * @param playerName
	 * @return
	 */
	private boolean isCombatTagged( String playerName )
	{
		if ( this._combattag == null ) return false;
		if ( !this._combattag.hasDataContainer( playerName ) ) return false;
		if ( this._combattag.getPlayerData( playerName ).hasPVPtagExpired() ) return false;
		return true;
	}

}
