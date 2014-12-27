package battlecode.world;

import static battlecode.common.GameActionExceptionType.NOT_ACTIVE;
import static battlecode.common.GameActionExceptionType.CANT_DO_THAT_BRO;
import static battlecode.common.GameActionExceptionType.CANT_SENSE_THAT;
import static battlecode.common.GameActionExceptionType.MISSING_UPGRADE;
import static battlecode.common.GameActionExceptionType.NOT_ENOUGH_RESOURCE;
import static battlecode.common.GameActionExceptionType.NO_ROBOT_THERE;
import static battlecode.common.GameActionExceptionType.OUT_OF_RANGE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import battlecode.common.CommanderSkillType;
import battlecode.common.DependencyProgress;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TerrainTile;
import battlecode.common.Upgrade;
import battlecode.engine.GenericController;
import battlecode.engine.instrumenter.RobotDeathException;
import battlecode.engine.instrumenter.RobotMonitor;
import battlecode.world.signal.AttackSignal;
import battlecode.world.signal.CastSignal;
import battlecode.world.signal.IndicatorDotSignal;
import battlecode.world.signal.IndicatorLineSignal;
import battlecode.world.signal.IndicatorStringSignal;
import battlecode.world.signal.LocationSupplyChangeSignal;
import battlecode.world.signal.MatchObservationSignal;
import battlecode.world.signal.MineSignal;
import battlecode.world.signal.MovementSignal;
import battlecode.world.signal.ResearchSignal;
import battlecode.world.signal.SpawnSignal;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;


/*
TODO:
- tweak player
-specs & software page?
- performance tips
- figure out why it's not deterministic

- optimize
- non-constant java.util costs
- player's navigation class, and specs about it
- fix execute action hack w/ robotmonitor ??
- fix playerfactory/spawnsignal hack??
- better suicide() ??
- pare down GW, GWviewer methods; add engine.getallsignals?
*/

public class RobotControllerImpl extends ControllerShared implements RobotController, GenericController {

    public RobotControllerImpl(GameWorld gw, InternalRobot r) {
        super(gw, r);
    }

    public int hashCode() {
        return robot.getID();
    }

    // *********************************
    // ****** GLOBAL QUERY METHODS *****
    // *********************************

    public int getMapWidth() {
        return robot.myGameWorld.getGameMap().getWidth();
    }

    public int getMapHeight() {
        return robot.myGameWorld.getGameMap().getHeight();
    }

    public double getTeamOre() {
        return gameWorld.resources(getTeam());
    }
    
    public void assertHaveResource(double amount) throws GameActionException {
        if (amount > gameWorld.resources(getTeam()))
            throw new GameActionException(NOT_ENOUGH_RESOURCE, "You do not have enough ORE to do that.");
    }
    
    // *********************************
    // ****** UNIT QUERY METHODS *******
    // *********************************

    public int getID() {
        return robot.getID();
    }

    public Team getTeam() {
        return robot.getTeam();
    }

    public RobotType getType() {
        return robot.type;
    }

    public MapLocation getLocation() {
        return robot.getLocation();
    }

    public double getTurnsUntilMovement() {
        return robot.getTimeUntilMovement();
    }

    public double getTurnsUntilAttack() {
        return robot.getTimeUntilAttack();
    }

    public double getHealth() {
        return robot.getHealthLevel();
    }

    public double getSupplyLevel() {
        return robot.getSupplyLevel();
    }
    
    public int getXP() {
        return robot.getXP();
    }

    public int getMissileCount() {
        return robot.getMissileCount();
    }

    // ***********************************
    // ****** GENERAL SENSOR METHODS *****
    // ***********************************

    public boolean checkCanSense(MapLocation loc) {
        
        int sensorRadius = robot.type.sensorRadiusSquared;

        if (robot.myLocation.distanceSquaredTo(loc) <= sensorRadius) {
            return true;
        }
       
        for (InternalObject o : gameWorld.allObjects()) {
            if ((Robot.class.isInstance(o)) && (o.getTeam() == robot.getTeam() || loc.distanceSquaredTo(o.getLocation()) <= sensorRadius)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean checkCanSense(InternalObject obj) {
        return obj.exists() && (obj.getTeam() == getTeam() || checkCanSense(obj.getLocation()));
    }

    public void assertCanSense(MapLocation loc) throws GameActionException {
        if (!checkCanSense(loc))
            throw new GameActionException(CANT_SENSE_THAT, "That location is not within the robot's sensor range.");
    }

    public void assertCanSense(InternalObject obj) throws GameActionException {
        if (!checkCanSense(obj))
            throw new GameActionException(CANT_SENSE_THAT, "That object is not within the robot's sensor range.");
    }

    public MapLocation senseHQLocation() {
        return gameWorld.getBaseHQ(getTeam()).getLocation();
    }
    
    public MapLocation senseEnemyHQLocation() {
        return gameWorld.senseEnemyHQLocation(getTeam());
    }

    public TerrainTile senseTerrainTile(MapLocation loc) {
        assertNotNull(loc);
        return gameWorld.senseMapTerrain(getTeam(), loc);
    }
    
    public boolean canSenseObject(GameObject o) {
        return checkCanSense(castInternalObject(o));
    }

    public boolean canSenseSquare(MapLocation loc) {
        return checkCanSense(loc);
    }

    public boolean isLocationOccupied(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        assertCanSense(loc);
        InternalRobot obj = (InternalRobot) gameWorld.getObject(loc);
        return obj != null;
    }

    public RobotInfo senseRobotAtLocation(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        assertCanSense(loc);
        InternalRobot obj = (InternalRobot) gameWorld.getObject(loc);
        if (obj != null && checkCanSense(obj)) {
            return obj.getRobotInfo();
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends GameObject> T[] senseNearbyGameObjects(final Class<T> type) {
        Predicate<InternalObject> p = new Predicate<InternalObject>() {
            public boolean apply(InternalObject o) {
                return checkCanSense(o) && (type.isInstance(o)) && (!o.equals(robot));
            }
        };
        return Iterables.toArray((Iterable<T>) Iterables.filter(gameWorld.allObjects(), p), type);
    }

    // USE THIS METHOD CAREFULLY
    public <T extends GameObject> RobotInfo[] getRobotsFromGameObjects(T[] array) {
        RobotInfo[] robots = new RobotInfo[array.length];
        for (int i = 0; i < robots.length; ++i) {
            InternalRobot ir = (InternalRobot) array[i];
            robots[i] = ir.getRobotInfo();
        }
        return robots;
    }

    public RobotInfo[] senseNearbyRobots() {
        return getRobotsFromGameObjects(senseNearbyGameObjects(Robot.class));
    }
    
    @SuppressWarnings("unchecked")
    public <T extends GameObject> T[] senseNearbyGameObjects(final Class<T> type, final int radiusSquared) {
        Predicate<InternalObject> p = new Predicate<InternalObject>() {
            public boolean apply(InternalObject o) {
                return o.myLocation.distanceSquaredTo(robot.myLocation) <= radiusSquared 
                        && checkCanSense(o) && (type.isInstance(o)) && (!o.equals(robot));
            }
        };
        return Iterables.toArray((Iterable<T>) Iterables.filter(gameWorld.allObjects(), p), type);
    }

    public RobotInfo[] senseNearbyRobots(int radiusSquared) {
        return getRobotsFromGameObjects(senseNearbyGameObjects(Robot.class, radiusSquared));
    }

    @SuppressWarnings("unchecked")
    public <T extends GameObject> T[] senseNearbyGameObjects(final Class<T> type, final int radiusSquared, final Team team) {
        if (team == null) {
            return senseNearbyGameObjects(type, radiusSquared);
        }
        Predicate<InternalObject> p = new Predicate<InternalObject>() {
            public boolean apply(InternalObject o) {
                return o.myLocation.distanceSquaredTo(robot.myLocation) <= radiusSquared
                        && o.getTeam() == team
                        && checkCanSense(o) && (type.isInstance(o)) && (!o.equals(robot));
            }
        };
        return Iterables.toArray((Iterable<T>) Iterables.filter(gameWorld.allObjects(), p), type);
    }

    public RobotInfo[] senseNearbyRobots(int radiusSquared, Team team) {
        return getRobotsFromGameObjects(senseNearbyGameObjects(Robot.class, radiusSquared, team));
    }

    @SuppressWarnings("unchecked")
    public <T extends GameObject> T[] senseNearbyGameObjects(final Class<T> type, final MapLocation center, final int radiusSquared, final Team team) {
        if (team == null) {
            Predicate<InternalObject> p = new Predicate<InternalObject>() {
                public boolean apply(InternalObject o) {
                    return o.myLocation.distanceSquaredTo(center) <= radiusSquared
                            && checkCanSense(o) && (type.isInstance(o)) && (!o.equals(robot));
                }
            };
            return Iterables.toArray((Iterable<T>) Iterables.filter(gameWorld.allObjects(), p), type);
        }
        Predicate<InternalObject> p = new Predicate<InternalObject>() {
            public boolean apply(InternalObject o) {
                return o.myLocation.distanceSquaredTo(center) <= radiusSquared
                        && o.getTeam() == team
                        && checkCanSense(o) && (type.isInstance(o)) && (!o.equals(robot));
            }
        };
        return Iterables.toArray((Iterable<T>) Iterables.filter(gameWorld.allObjects(), p), type);
    }

    public RobotInfo[] senseNearbyRobots(MapLocation center, int radiusSquared, Team team) {
        return getRobotsFromGameObjects(senseNearbyGameObjects(Robot.class, center, radiusSquared, team));
    }

    // ***********************************
    // ****** MOVEMENT METHODS ***********
    // ***********************************

    public boolean isMovementActive() {
        return getTurnsUntilMovement() < 1;
    }

    public void assertIsMovementActive() throws GameActionException {
        if (!isMovementActive()) {
            throw new GameActionException(NOT_ACTIVE, "This robot has movement delay.");
        }
    }

    public void assertValidRobotPlacement(RobotType type, MapLocation loc) throws GameActionException {
        if (!isPathable(type, loc)) {
            throw new GameActionException(GameActionExceptionType.CANT_MOVE_THERE, "Cannot move robot of given type to that location.");
        }
    }

    public boolean isPathable(RobotType type, MapLocation loc) {
        return gameWorld.canMove(loc, type);
    }

    public boolean isMovingUnit() {
        return robot.type.canMove();
    }

    public void assertIsMovingUnit() throws GameActionException {
        if (!isMovingUnit()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "This unit cannot move.");
        }
    }

    protected void assertValidDirection(Direction d) {
        assertNotNull(d);
        if (d == Direction.NONE || d == Direction.OMNI) {
            throw new IllegalArgumentException("You cannot move in the direction NONE or OMNI.");
        }
    }

    public boolean isValidMoveDirection(Direction d) {
        if (d == null || d == Direction.NONE || d == Direction.OMNI)
            return false;
        return isMovingUnit() && gameWorld.canMove(getLocation().add(d), robot.type);
    }

    public boolean canMove(Direction dir) {
        return isMovingUnit() && isValidMoveDirection(dir);
    }

    public void move(Direction d) throws GameActionException {
        assertIsMovementActive();
        assertIsMovingUnit();
        assertValidDirection(d);
        assertValidRobotPlacement(robot.type, getLocation().add(d));
        double delay = robot.calculateMovementActionDelay(getLocation(), getLocation().add(d), senseTerrainTile(getLocation()));

        robot.activateMovement(new MovementSignal(robot, getLocation().add(d),
                true, (int) delay), robot.getLoadingDelayForType(), delay);
    }

    // ***********************************
    // ****** ATTACK METHODS *************
    // ***********************************

    public boolean isAttackActive() {
        return getTurnsUntilAttack() < 1;
    }

    public boolean isAttackingUnit() {
        return robot.type.canAttack();
    }

    public boolean canAttackLocation(MapLocation loc) {
        return isAttackingUnit() && isValidAttackLocation(loc) && robot.type != RobotType.BASHER;
    }

    protected void assertIsAttackActive() throws GameActionException {
        if (!isAttackActive())
            throw new GameActionException(NOT_ACTIVE, "This robot has action delay and cannot attack.");
    }

    protected void assertIsAttackingUnit() throws GameActionException {
        if (!isAttackingUnit()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Not attacking unit.");
        }
    }

    protected void assertValidAttackLocation(MapLocation loc) throws GameActionException {
        if (!isValidAttackLocation(loc))
            throw new GameActionException(OUT_OF_RANGE, "That location is out of this robot's attack range");
    }

    public boolean isValidAttackLocation(MapLocation loc) {
        assertNotNull(loc);
        return isAttackingUnit() && gameWorld.canAttackSquare(robot, loc);
    }

    public void attackLocation(MapLocation loc) throws GameActionException {
        assertIsAttackActive();
        assertIsAttackingUnit();
        assertNotNull(loc);
        assertValidAttackLocation(loc);
        if (robot.type == RobotType.BASHER) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Bashers can only attack using the bash() method.");
        }

        robot.activateAttack(new AttackSignal(robot, loc), robot.getAttackDelayForType(), robot.getCooldownDelayForType());
    }

    public void bash() throws GameActionException {
        assertIsAttackActive();
        if (robot.type != RobotType.BASHER) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Only Bashers can attack using the attack() method.");
        }

        robot.activateAttack(new AttackSignal(robot, getLocation()), robot.getAttackDelayForType(), robot.getCooldownDelayForType());
    }

    public void explode() throws GameActionException {
        if (robot.type == RobotType.MISSILE) {
            robot.setSelfDestruct();
        }
        throw new RobotDeathException();
    }

    //***********************************
    //****** COMMANDER METHODS **********
    //***********************************

    public boolean hasCommander() {
        return gameWorld.hasCommander(robot.getTeam());
    }

    public boolean hasLearnedSkill(CommanderSkillType skill) throws GameActionException {
        if (!hasCommander()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Cannot call hasLearnedSkill without a Commander.");
        }
        return gameWorld.hasSkill(robot.getTeam(), skill);
    }

    public void castFlash(MapLocation loc) throws GameActionException {
        assertNotNull(loc);

        if (robot.type != RobotType.COMMANDER) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Only Commanders can cast Flash.");
        }

        //is this kosher? i hope so
        
        assertIsMovementActive();
        if (!gameWorld.canMove(loc, robot.type)) {
            throw new GameActionException(GameActionExceptionType.CANT_MOVE_THERE, "Cannot teleport to " + loc.toString());
        }
        else {
            robot.activateMovement(new CastSignal(robot, loc), robot.getLoadingDelayForType(), GameConstants.FLASH_MOVEMENT_DELAY);
        }
    }

    public int getFlashCooldown() throws GameActionException {
        if (!hasCommander()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Cannot call getFlashCooldown without a Commander.");
        } 
        if (!hasLearnedSkill(CommanderSkillType.FLASH)) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Cannot call getFlashCooldown without having learned Flash.");
        }
        return gameWorld.getSkillCooldown(robot.getTeam(), CommanderSkillType.FLASH);
    }

    // ***********************************
    // ****** BROADCAST METHODS **********
    // ***********************************

    public boolean hasBroadcasted() {
        return robot.hasBroadcasted();
    }
    
    public void broadcast(int channel, int data) throws GameActionException {
        if (channel<0 || channel>GameConstants.BROADCAST_MAX_CHANNELS)
            throw new GameActionException(CANT_DO_THAT_BRO, "Can only use radio channels from 0 to "+GameConstants.BROADCAST_MAX_CHANNELS+", inclusive");
        
        robot.addBroadcast(channel, data);
    }
    
    @Override
    public int readBroadcast(int channel) throws GameActionException {
        if (channel<0 || channel>GameConstants.BROADCAST_MAX_CHANNELS)
            throw new GameActionException(CANT_DO_THAT_BRO, "Can only use radio channels from 0 to "+GameConstants.BROADCAST_MAX_CHANNELS+", inclusive");
	Integer queued = robot.getQueuedBroadcastFor(channel);
	if (queued != null) {
	    return queued.intValue();
	}
        int m = gameWorld.getMessage(robot.getTeam(), channel);
        return m;
    }

    // ***********************************
    // ****** SUPPLY METHODS *************
    // ***********************************

    public double senseSupplyLevelAtLocation(MapLocation loc) throws GameActionException {
        return gameWorld.senseSupplyLevel(getTeam(), loc);
    }

    public void dropSupplies(int amount) throws GameActionException {
        robot.dropSupply(amount);
    }

    public void pickUpSupplies(int amount) throws GameActionException {
        robot.pickUpSupply(amount);
    }

    public void transferSupplies(int amount, MapLocation loc) throws GameActionException {
        if (loc.distanceSquaredTo(getLocation()) > GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Can't transfer supply that much distance.");
        }
        InternalRobot obj = (InternalRobot) gameWorld.getObject(loc);
        if (obj == null) {
            throw new GameActionException(CANT_DO_THAT_BRO, "No one to receive supply from transfer in that direction.");
        }
        robot.transferSupply(amount, obj);
    }

    public void transferSuppliesToHQ() throws GameActionException {
        if (robot.type != RobotType.SUPPLYDEPOT) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Only supply depot can transfer supplies to hq");
        }

        robot.transferSupply(Integer.MAX_VALUE, gameWorld.getBaseHQ(robot.getTeam()));
    }

    // ***********************************
    // ****** MINING METHODS *************
    // ***********************************

    public boolean isMiningUnit() {
        return robot.type.canMine();
    }

    public boolean canMine() {
        return isMiningUnit();
    }

    public void assertIsMiningUnit() throws GameActionException {
        if (!isMiningUnit()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Cannot mine.");
        }
    }

    public void mine() throws GameActionException {
        assertIsMovementActive();
        assertIsMiningUnit();
        MapLocation loc = getLocation();
		
		robot.activateMovement(new MineSignal(loc, getTeam(), getType()), GameConstants.MINING_LOADING_DELAY, GameConstants.MINING_MOVEMENT_DELAY);
    }

    public double senseOre(MapLocation loc) throws GameActionException {
        return gameWorld.senseOre(getTeam(), loc);
    }   

    // ***********************************
    // ****** LAUNCHER *******************
    // ***********************************

    public boolean isLaunchingUnit() {
        return robot.type.canLaunch();
    }

    public boolean canLaunch(Direction dir) {
        if (!isLaunchingUnit()) {
            return false;
        }

        MapLocation loc = getLocation().add(dir);
        if (!gameWorld.canMove(loc, RobotType.MISSILE)) {
            return false;
        }

        return !robot.movedThisTurn() && robot.getMissileCount() > 0;
    }

    public void launchMissile(Direction dir) throws GameActionException {
        if (!isLaunchingUnit()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Only LAUNCHER can launch missiles");
        }

        if (robot.getMissileCount() == 0) {
            throw new GameActionException(CANT_DO_THAT_BRO, "No missiles to launch");
        }

        if (robot.movedThisTurn()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Launchers can't move and launch in the same turn.");
        }

        MapLocation loc = getLocation().add(dir);
        if (!gameWorld.canMove(loc, RobotType.MISSILE))
            throw new GameActionException(GameActionExceptionType.CANT_MOVE_THERE, "That square is occupied.");

        if (!robot.canLaunchMissileAtLocation(loc)) {
            throw new GameActionException(GameActionExceptionType.CANT_MOVE_THERE, "Missile already launched in that direction.");
        }

        robot.decrementMissileCount();
        robot.launchMissile(loc);
    }

    // ***********************************
    // ****** BUILDING/SPAWNING **********
    // ***********************************

    public DependencyProgress checkDependencyProgress(RobotType type) {
        if (gameWorld.getRobotTypeCount(robot.getTeam(), type) > 0) {
            return DependencyProgress.DONE;
        } else if (gameWorld.getTotalRobotTypeCount(robot.getTeam(), type) > 0) {
            return DependencyProgress.INPROGRESS;
        } else {
            return DependencyProgress.NONE;
        }
    }

    public boolean isSpawningUnit() {
        return robot.type.canSpawn();
    }

    public boolean canSpawn(Direction dir, RobotType type) {
        MapLocation loc = getLocation().add(dir);
        if (!gameWorld.canMove(loc, robot.type)) {
            return false;
        }

        if (!isSpawningUnit() || type == RobotType.COMMANDER && hasCommander()) {
            return false;
        }
        double cost = type.oreCost;
        if (type == RobotType.COMMANDER) {
            cost *= (1 << Math.min(gameWorld.getCommandersSpawned(robot.getTeam()), 8));
        }
        if (cost > gameWorld.resources(getTeam())) {
            return false;
        }

        return true;
    }

    public void spawn(Direction dir, RobotType type) throws GameActionException {
        if (!isSpawningUnit()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Only certain buildings can spawn");
        }
        if (type.spawnSource != robot.type) {
            throw new GameActionException(CANT_DO_THAT_BRO, "This spawn can only be by a certain type");
        }
        if (type == RobotType.COMMANDER && hasCommander()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Only one commander per team!");
        }

        assertIsMovementActive();

        double cost = type.oreCost;
        if (type == RobotType.COMMANDER) {
            cost *= (1 << Math.min(gameWorld.getCommandersSpawned(robot.getTeam()), 8));
        }
        assertHaveResource(cost);

        MapLocation loc = getLocation().add(dir);
        if (!gameWorld.canMove(loc, type))
            throw new GameActionException(GameActionExceptionType.CANT_MOVE_THERE, "That square is occupied.");

        robot.activateMovement(
                new SpawnSignal(loc, type, robot.getTeam(), robot, 0), 0, type.buildTurns);
    }

    public boolean isBuildingUnit() {
        return robot.type.canBuild();
    }

    public boolean canBuild(Direction dir, RobotType type) {
        if (!type.isBuilding)
            return false;

        // check dependencies
        if (gameWorld.getRobotTypeCount(getTeam(), type.dependency) == 0) {
            return false;
        }

        double cost = type.oreCost;
        if (cost > gameWorld.resources(getTeam())) {
            return false;
        }

        MapLocation loc = getLocation().add(dir);
        if (!gameWorld.canMove(loc, robot.type))
            return false;

        return true;
    }
    
    public void build(Direction dir, RobotType type) throws GameActionException {
        if (!isBuildingUnit())
            throw new GameActionException(CANT_DO_THAT_BRO, "Only BEAVER can build");
        if (!type.isBuilding)
            throw new GameActionException(CANT_DO_THAT_BRO, "Can only build buildings");

        // check dependencies
        if (gameWorld.getRobotTypeCount(getTeam(), type.dependency) == 0) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Missing depency for build of " + type);
        }

        assertIsMovementActive();

        double cost = type.oreCost;
        
        assertHaveResource(cost);

        MapLocation loc = getLocation().add(dir);
        if (!gameWorld.canMove(loc, type))
            throw new GameActionException(GameActionExceptionType.CANT_MOVE_THERE, "That square is occupied.");

        int delay = type.buildTurns;

        robot.activateMovement(
                new SpawnSignal(loc, type, robot.getTeam(), robot, delay), delay, delay);
    }

    //***********************************
    //****** UPGRADE METHODS ************
    //***********************************

    public boolean isResearchingUnit() {
        return robot.type.canResearch();
    }

    public boolean canResearch(Upgrade upgrade) {
        if (!isResearchingUnit()) {
            return false;
        }

        if (gameWorld.hasUpgrade(getTeam(), upgrade)) {
            return false;
        }

        if (checkResearchProgress(upgrade) > 0) {
            return false;
        }

        if (upgrade.oreCost > gameWorld.resources(getTeam())) {
            return false;
        }

        return true;
    }

    public boolean hasUpgrade(Upgrade upgrade) {
        assertNotNull(upgrade);
        return gameWorld.hasUpgrade(getTeam(), upgrade);
    }

    public void assertHaveUpgrade(Upgrade upgrade) throws GameActionException {
        if (!gameWorld.hasUpgrade(getTeam(), upgrade))
            throw new GameActionException(MISSING_UPGRADE, "You need the following upgrade: "+upgrade);
    }

    public void researchUpgrade(Upgrade upgrade) throws GameActionException {
        if (!isResearchingUnit()) {
            throw new GameActionException(CANT_DO_THAT_BRO, "Only HQ can research.");
        }
        if (gameWorld.hasUpgrade(getTeam(), upgrade))
            throw new GameActionException(CANT_DO_THAT_BRO, "You already have that upgrade. ("+upgrade+")");
        if (checkResearchProgress(upgrade) > 0) {
            throw new GameActionException(CANT_DO_THAT_BRO, "You already started researching this upgrade. ("+upgrade+")");
        }
        assertIsMovementActive();
        assertHaveResource(upgrade.oreCost);
        gameWorld.adjustResources(getTeam(), -upgrade.oreCost);
        robot.activateResearch(new ResearchSignal(robot, upgrade), upgrade.numRounds, upgrade.numRounds);
    }
    
    public int checkResearchProgress(Upgrade upgrade) {
        return gameWorld.getUpgradeProgress(getTeam(), upgrade);
    }
   
    // ***********************************
    // ****** OTHER ACTION METHODS *******
    // ***********************************

    public void yield() {
        RobotMonitor.endRunner();
    }

    public void disintegrate() {
        throw new RobotDeathException();
    }
    
    public void resign() {
        for (InternalObject obj : gameWorld.getAllGameObjects())
            if ((obj instanceof InternalRobot) && obj.getTeam() == robot.getTeam())
                gameWorld.notifyDied((InternalRobot) obj);
        gameWorld.removeDead();
    }

    // ***********************************
    // ******** MISC. METHODS ************
    // ***********************************

    public void setTeamMemory(int index, long value) {
        gameWorld.setTeamMemory(robot.getTeam(), index, value);
    }

    public void setTeamMemory(int index, long value, long mask) {
        gameWorld.setTeamMemory(robot.getTeam(), index, value, mask);
    }

    public long[] getTeamMemory() {
        long[] arr = gameWorld.getOldTeamMemory()[robot.getTeam().ordinal()];
        return Arrays.copyOf(arr, arr.length);
    }

    // ***********************************
    // ******** DEBUG METHODS ************
    // ***********************************

    public void setIndicatorString(int stringIndex, String newString) {
        if (stringIndex >= 0 && stringIndex < GameConstants.NUMBER_OF_INDICATOR_STRINGS)
            (new IndicatorStringSignal(robot, stringIndex, newString)).accept(gameWorld);
    }

    public void setIndicatorDot(MapLocation loc, int red, int green, int blue) {
        assertNotNull(loc);
        new IndicatorDotSignal(robot,loc,red,green,blue).accept(gameWorld);
    }

    public void setIndicatorLine(MapLocation from, MapLocation to, int red, int green, int blue) {
        assertNotNull(from);
        assertNotNull(to);
        new IndicatorLineSignal(robot,from,to,red,green,blue).accept(gameWorld);
    }

    public long getControlBits() {
        return robot.getControlBits();
    }

    public void addMatchObservation(String observation) {
        (new MatchObservationSignal(robot, observation)).accept(gameWorld);
    }

    public void breakpoint() {
        gameWorld.notifyBreakpoint();
    }
}
