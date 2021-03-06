package battlecode.world;

import battlecode.common.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for RobotController. These are where the gameplay tests are.
 *
 * Using TestGame and TestMapGenerator as helpers.
 */
public class RobotControllerTest {
    public final double EPSILON = 1.0e-9;

    /**
     * Tests the most basic methods of RobotController. This test has extra
     * comments to serve as an example of how to use TestMapGenerator and
     * TestGame.
     *
     * @throws GameActionException shouldn't happen
     */
    @Test
    public void testBasic() throws GameActionException {
        // Prepares a map with the following properties:
        // width = 10, height = 10, num rounds = 100
        // random seed = 1337
        // parts(1, 0) = parts(0, 1) = 10
        // The map doesn't have to meet specs.
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100)
                .withSeed(1337)
                .withParts(1, 0, 30)
                .withParts(0, 1, 30)
                .withRubble(2, 2, 200);

        // This creates the actual GameMap.
        GameMap map = mapGen.getMap("test");

        // This creates the actual game.
        TestGame game = new TestGame(map);

        // Let's spawn a robot for each team. The integers represent IDs.
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archonA = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int soldierB = game.spawn(oX + 1, oY + 1, RobotType.SOLDIER, Team
                .B);
        InternalRobot archonABot = game.getBot(archonA);

        assertEquals(archonABot.getLocation(), new MapLocation(oX, oY));

        // The following specifies the code to be executed in the next round.
        // Bytecodes are not counted, and yields are automatic at the end.
        game.round((id, rc) -> {
            if (id == archonA) {
                rc.move(Direction.EAST);
            } else if (id == soldierB) {
                // do nothing
            }
        });

        // Let's assert that things happened properly.
        assertEquals(archonABot.getLocation(), new MapLocation(oX + 1, oY));
        assertEquals(game.getWorld().resources(Team.A), 30 + GameConstants
                .PARTS_INITIAL_AMOUNT + GameConstants.ARCHON_PART_INCOME -
                        GameConstants.PART_INCOME_UNIT_PENALTY, EPSILON);

        // Lets 10 rounds go by.
        game.waitRounds(10);

        // Let's make sure that robots can attack each other.
        game.round((id, rc) -> {
            if (id == soldierB) {
                rc.attackLocation(new MapLocation(oX + 1, oY));
            }
        });

        // Makes sure that the attack did damage.
        assertEquals(archonABot.getHealthLevel(), 996, EPSILON);
    }

    /**
     * This test verifies rubble behavior.
     * <p>
     * 1) Clearing rubble doesn't go below 0, and follows the right formula
     * 2) You can't move onto tiles with >= 100 rubble
     * 3) Dying produces rubble equal to your max health.
     *
     * @throws GameActionException shouldn't happen
     */
    @Test
    public void testRubbleBasic() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100)
                .withRubble(0, 1, 2)
                .withRubble(1, 0, 100)
                .withRubble(1, 1, 99);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int soldierA = game.spawn(oX, oY, RobotType.SOLDIER, Team.A);
        final int soldierB = game.spawn(oX+3, oY+2, RobotType.SOLDIER, Team.B);
        final int stdZombie = game.spawn(oX+3, oY+3, RobotType.STANDARDZOMBIE, Team.ZOMBIE);
        InternalRobot soldierABot = game.getBot(soldierA);
        InternalRobot soldierBBot = game.getBot(soldierB);
        InternalRobot stdZombieBot = game.getBot(stdZombie);

        game.round((id, rc) -> {
            if (id == soldierA) {
                assertFalse(rc.canMove(Direction.EAST));
                rc.clearRubble(Direction.EAST);
            }
        });

        game.waitRounds(10);

        game.round((id, rc) -> {
            if (id == soldierA) {
                assertTrue(rc.canMove(Direction.SOUTH));
                rc.clearRubble(Direction.SOUTH);
            }
        });

        game.waitRounds(10);

        game.round((id, rc) -> {
            if (id == soldierA) {
                assertTrue(rc.canMove(Direction.SOUTH_EAST));
                rc.clearRubble(Direction.SOUTH_EAST);
            } else if (id == stdZombie) { // Attack soldierB to infect it
                rc.attackLocation(new MapLocation(oX+3,oY+2));
            }
        });

        game.waitRounds(5);

        // Die to make sure that the robot produces rubble.
        // The damage taken is to make sure the rubble is based on max
        // health, not previous health.
        soldierABot.takeDamage(RobotType.SOLDIER.maxHealth - 3);
        soldierBBot.takeDamage(RobotType.SOLDIER.maxHealth - 3);
        stdZombieBot.takeDamage(RobotType.STANDARDZOMBIE.maxHealth - 2);
        game.round((id, rc) -> {
            if (id == soldierA) { // All robots kill themselves
                rc.attackLocation(new MapLocation(oX, oY));
            } else if (id == soldierB) {
                rc.attackLocation(new MapLocation(oX + 3, oY + 2));
            } else if (id == stdZombie) {
                rc.attackLocation(new MapLocation(oX + 3, oY + 3));
            }
        });

        // Make sure the rubble amounts are correct.
        assertEquals(game.getWorld().getRubble(new MapLocation(oX, oY)),
                RobotType.SOLDIER.maxHealth, EPSILON);
        assertEquals(game.getWorld().getRubble(new MapLocation(oX, oY + 1)),
                0, EPSILON);
        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 1, oY)),
                100 * (1 - GameConstants.RUBBLE_CLEAR_PERCENTAGE) -
                        GameConstants
                                .RUBBLE_CLEAR_FLAT_AMOUNT, EPSILON);
        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 1, oY + 1)),
                99 * (1 - GameConstants.RUBBLE_CLEAR_PERCENTAGE) - GameConstants
                        .RUBBLE_CLEAR_FLAT_AMOUNT, EPSILON);
        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 3, oY + 2)),
                0, EPSILON); // soldierB turns into zombie and doesn't leave rubble
        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 3, oY + 3)),
                60, EPSILON); // dead zombie leaves rubble
        assertEquals(game.getWorld().getRobot(new MapLocation(oX+3, oY+2)).getType(),
                RobotType.STANDARDZOMBIE); // soldierB actually did turn into a zombie
    }

    /**
     * Verifies that moving onto tiles with rubble doubles your core and move
     * delay changes.
     */
    @Test
    public void testRubbleSlow() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100)
                .withRubble(0, 1, 49)
                .withRubble(0, 2, 51);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int soldierA = game.spawn(oX, oY, RobotType.SOLDIER, Team.A);
        InternalRobot soldierABot = game.getBot(soldierA);

        game.round((id, rc) -> {
            if (id == soldierA) {
                rc.move(Direction.SOUTH);
            }
        });

        assertEquals(soldierABot.getCoreDelay(), RobotType.SOLDIER
                .movementDelay, EPSILON);
        assertEquals(soldierABot.getWeaponDelay(), RobotType.SOLDIER
                .cooldownDelay, EPSILON);

        game.waitRounds(10);

        game.round((id, rc) -> {
            if (id == soldierA) {
                rc.move(Direction.SOUTH);
            }
        });

        assertEquals(soldierABot.getCoreDelay(), RobotType.SOLDIER
                .movementDelay * 2, EPSILON);
        assertEquals(soldierABot.getWeaponDelay(), RobotType.SOLDIER
                .cooldownDelay * 2, EPSILON);
    }


    /**
     * Test that zombies can see everything.
     */
    @Test
    public void testZombieSightRange() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(100, 100, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int zombie = game.spawn(oX, oY, RobotType.FASTZOMBIE, Team
                .ZOMBIE);
        final int soldier = game.spawn(oX + 99, oY + 99, RobotType.SOLDIER,
                Team.B);

        game.round((id, rc) -> {
            if (id == zombie) {
                RobotInfo[] nearby = rc.senseNearbyRobots();
                assertEquals(nearby.length, 1);
            } else if (id == soldier) {
                RobotInfo[] nearby = rc.senseNearbyRobots();
                assertEquals(nearby.length, 0);
            }
        });
    }

    /**
     * Test Map Memory scenarios.
     * <p>
     * 0) You should not be able to sense values of parts and rubble out of
     * range.
     * 1) You should be able to sense values of parts and rubble in range.
     * 2) After moving out of range, your sensed value should reflect the
     * latest change.
     * 3) Sanity check that zombies work due to their infinite sight range.
     *
     * Note: this test hard-codes the soldier sight range of 24.
     */
    @Test
    public void testSenses() throws GameActionException {
        final double rubbleVal = 100;
        final double partsVal = 30;
        TestMapGenerator mapGen = new TestMapGenerator(100, 100, 100)
                .withRubble(0, 5, rubbleVal)
                .withParts(5, 0, partsVal)
                .withParts(20,0, partsVal);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int zombie = game.spawn(oX + 99, oY + 99, RobotType.FASTZOMBIE,
                Team.ZOMBIE);
        final int soldier = game.spawn(oX, oY, RobotType.SOLDIER, Team.B);
        // robots to clear rubble and take parts
        final int soldier2 = game.spawn(oX, oY + 6, RobotType.SOLDIER, Team.A);
        final int archon3 = game.spawn(oX + 6, oY, RobotType.ARCHON, Team.A);
        MapLocation loc1 = new MapLocation(oX, oY + 5);
        MapLocation loc2 = new MapLocation(oX + 5, oY);

        // Zombie can see everything. Soldier can only see values in range.
        game.round((id, rc) -> {
            if (id == zombie) {
                assertEquals(rc.senseRubble(loc1), rubbleVal, EPSILON);
                assertEquals(rc.senseParts(loc2), partsVal, EPSILON);
            } else if (id == soldier) {
                assertEquals(rc.senseRubble(loc1), -1, EPSILON);
                assertEquals(rc.senseParts(loc2), -1, EPSILON);
                MapLocation[] partLocs = rc.sensePartLocations(-1);
                assertEquals(partLocs.length,0);
            } else if (id == archon3) {
                MapLocation[] partLocs = rc.sensePartLocations(-1);
                assertEquals(partLocs.length,1);
                assertEquals(partLocs[0],loc2);
                partLocs = rc.sensePartLocations(0);
                assertEquals(partLocs.length,0);
                partLocs = rc.sensePartLocations(1000);
                assertEquals(partLocs.length,1);
            }
        });

        // Soldier moving closer results in proper value being sensed.
        game.round((id, rc) -> {
            if (id == soldier) {
                rc.move(Direction.SOUTH_EAST);
            }
        });
        game.round((id, rc) -> {
            if (id == soldier) {
                assertEquals(rc.senseRubble(loc1), rubbleVal, EPSILON);
                assertEquals(rc.senseParts(loc2), partsVal, EPSILON);
            }
        });

        // Soldier moves away, should go back to -1
        game.waitRounds(10);
        game.round((id, rc) -> {
            if (id == soldier) {
                rc.move(Direction.NORTH_WEST);
            }
        });
        game.round((id, rc) -> {
            if (id == soldier) {
                assertFalse(rc.canSenseLocation(loc1));
                assertFalse(rc.canSenseLocation(loc2));
                assertEquals(rc.senseRubble(loc1), -1, EPSILON);
                assertEquals(rc.senseParts(loc2), -1, EPSILON);
            }
        });

        // If parts or rubble values change while you're out of range, you
        // shouldn't be able to sense the changes.
        game.round((id, rc) -> {
            if (id == soldier2) {
                rc.clearRubble(Direction.NORTH);
            } else if (id == archon3) {
                rc.move(Direction.WEST); // get parts
            }
        });
        game.round((id, rc) -> {
            if (id == soldier) {
                assertFalse(rc.canSenseLocation(loc1));
                assertFalse(rc.canSenseLocation(loc2));
                assertEquals(rc.senseRubble(loc1), -1, EPSILON);
                assertEquals(rc.senseParts(loc2), -1, EPSILON);
            }
        });

        // If you move back into the location, then you should be able to
        // sense the new values.
        game.waitRounds(10);
        game.round((id, rc) -> {
            if (id == soldier) {
                rc.move(Direction.SOUTH_EAST);
            }
        });
        final double rubbleVal2 = rubbleVal * (1 - GameConstants
                .RUBBLE_CLEAR_PERCENTAGE) -
                GameConstants.RUBBLE_CLEAR_FLAT_AMOUNT;
        game.round((id, rc) -> {
            if (id == soldier) {
                assertEquals(rc.senseRubble(loc1), rubbleVal2, EPSILON);
                assertEquals(rc.senseParts(loc2), 0, EPSILON);
            }
        });

        // If the rubble value changes while you're able to sense it, you
        // should be able to sense the new value. (Former bug)
        game.round((id, rc) -> {
            if (id == soldier2) {
                rc.clearRubble(Direction.NORTH);
            }
        });
        final double rubbleVal3 = rubbleVal2 * (1 - GameConstants
                .RUBBLE_CLEAR_PERCENTAGE) -
                GameConstants.RUBBLE_CLEAR_FLAT_AMOUNT;
        game.round((id, rc) -> {
            if (id == soldier) {
                assertEquals(rc.senseRubble(loc1), rubbleVal3, EPSILON);
            }
        });

        // If you move away, you should lose the ability to sense.
        // Zombies can always sense every location; make sure their senses
        // update correctly.
        game.round((id, rc) -> {
            if (id == soldier) {
                rc.move(Direction.NORTH_WEST);
            } else if (id == zombie) {
                rc.move(Direction.NORTH_WEST);
            }
        });
        game.round((id, rc) -> {
            if (id == zombie) {
                assertEquals(rc.senseRubble(loc1), rubbleVal3, EPSILON);
            }
            else if (id == soldier) {
                assertEquals(rc.senseRubble(loc1), -1, EPSILON);
            }
        });
    }

    /**
     * Ensure that actions take place immediately.
     */
    @Test
    public void testImmediateActions() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(3, 1, 20);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();

        final int a = game.spawn(oX, oY, RobotType.SOLDIER, Team.A);
        final int b = game.spawn(oX + 2, oY, RobotType.SOLDIER, Team.B);

        game.round((id, rc) -> {
            if (id != a) return;

            final MapLocation start = rc.getLocation();
            assertEquals(start, new MapLocation(oX, oY));

            rc.move(Direction.EAST);

            final MapLocation newLocation = rc.getLocation();
            assertEquals(newLocation, new MapLocation(oX + 1, oY));
        });

        // Let delays go away
        game.waitRounds(10);

        game.round((id, rc) -> {
            if (id != a) return;

            MapLocation bLoc = new MapLocation(oX + 2, oY);

            RobotInfo bInfo = rc.senseRobotAtLocation(bLoc);

            assertEquals(RobotType.SOLDIER.maxHealth, bInfo.health, .00001);

            rc.attackLocation(new MapLocation(oX + 2, oY));

            RobotInfo bInfoNew = rc.senseRobotAtLocation(bLoc);

            assertEquals(RobotType.SOLDIER.maxHealth - RobotType.SOLDIER
                            .attackPower,
                    bInfoNew.health,
                    .00001);
        });
    }

    /**
     * Tests the archon repair() method.
     */
    @Test
    public void testRepair() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int soldier = game.spawn(oX + 2, oY, RobotType.SOLDIER, Team.A);
        InternalRobot soldierBot = game.getBot(soldier);

        soldierBot.takeDamage(15);

        game.round((id, rc) -> {
            if (id == archon) {
                rc.repair(new MapLocation(oX + 2, oY));
            }
        });

        assertEquals(soldierBot.getHealthLevel(), RobotType.SOLDIER.maxHealth
                - 15 + GameConstants.ARCHON_REPAIR_AMOUNT, EPSILON);
    }

    /**
     * Makes sure that parts costs are properly subtracted when building a unit.
     */
    @Test
    public void testPartsCost() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY, RobotType.ARCHON, Team.A);

        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT, EPSILON);

        game.round((id, rc) -> {
            if (id == archon) {
                rc.build(Direction.SOUTH_EAST, RobotType.SOLDIER);
            }
        });

        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT - RobotType.SOLDIER.partCost +
                GameConstants.ARCHON_PART_INCOME - 2 * GameConstants
                        .PART_INCOME_UNIT_PENALTY, EPSILON);
    }

    /**
     * Destroying a zombie den should reward parts to the attacker's team.
     */
    @Test
    public void testDenPartsReward() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int soldierA = game.spawn(oX, oY + 1, RobotType.SOLDIER, Team.A);
        final int soldierB = game.spawn(oX + 1, oY, RobotType.SOLDIER, Team.B);
        final int den = game.spawn(oX, oY, RobotType.ZOMBIEDEN, Team.ZOMBIE);
        InternalRobot denBot = game.getBot(den);

        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT, EPSILON);

        // The den should have enough health to survive 2 attacks.
        denBot.takeDamage(RobotType.ZOMBIEDEN.maxHealth - RobotType.SOLDIER
                .attackPower - 1);

        // Soldier A goes first
        game.round((id, rc) -> {
            if (id == soldierA) {
                rc.attackLocation(new MapLocation(oX, oY));
            } else if (id == soldierB) {
                rc.attackLocation(new MapLocation(oX, oY));
            }
        });

        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT + GameConstants.ARCHON_PART_INCOME -
                GameConstants.PART_INCOME_UNIT_PENALTY, EPSILON);
        assertEquals(game.getWorld().resources(Team.B), GameConstants
                .PARTS_INITIAL_AMOUNT + GameConstants.ARCHON_PART_INCOME -
                GameConstants.PART_INCOME_UNIT_PENALTY + GameConstants
                .DEN_PART_REWARD, EPSILON);
    }

    /**
     * Using more bytecode should incur delay penalties
     */
    @Test
    public void testDelayPenalty() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 12);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int soldierA = game.spawn(oX, oY, RobotType.SOLDIER, Team.A);
        final int soldierB = game.spawn(oX + 1, oY + 1, RobotType.SOLDIER, Team
                .B);
        InternalRobot soldierABot = game.getBot(soldierA);
        InternalRobot soldierBBot = game.getBot(soldierB);

        soldierABot.setBytecodesUsed(0); // Start out using no bytecode
        soldierBBot.setBytecodesUsed(0);

        //Soldier A moves, soldier B attacks
        game.round((id, rc) -> {
            if (id == soldierA) {
                rc.move(Direction.EAST);
            } else if (id == soldierB) {
                rc.attackLocation(new MapLocation(oX+2,oY+2));
            }
        });

        // Core delay = movement delay, weapon delay = attack delay
        assertEquals(soldierABot.getCoreDelay(),RobotType.SOLDIER.movementDelay,EPSILON);
        assertEquals(soldierBBot.getWeaponDelay(),RobotType.SOLDIER.attackDelay,EPSILON);

        game.waitRounds(1);
        // After one round with zero bytecode, should decrement by one
        assertEquals(soldierABot.getCoreDelay(),RobotType.SOLDIER.movementDelay-1,EPSILON);
        assertEquals(soldierBBot.getWeaponDelay(),RobotType.SOLDIER.attackDelay-1,EPSILON);

        game.waitRounds(3);
        // Should have gone back to zero
        assertEquals(soldierABot.getCoreDelay(),0,EPSILON);
        assertEquals(soldierBBot.getWeaponDelay(),0,EPSILON);

        // Now use intermediate amount of bytecode
        soldierABot.setBytecodesUsed(RobotType.SOLDIER.bytecodeLimit-4000);
        soldierBBot.setBytecodesUsed(RobotType.SOLDIER.bytecodeLimit-4000);

        //Soldier A moves, soldier B attacks
        game.round((id, rc) -> {
            if (id == soldierA) {
                rc.move(Direction.WEST);
            } else if (id == soldierB) {
                rc.attackLocation(new MapLocation(oX+2,oY+2));
            }
        });

        // Core delay = movement delay, weapon delay = attack delay
        assertEquals(soldierABot.getCoreDelay(),RobotType.SOLDIER.movementDelay,EPSILON);
        assertEquals(soldierBBot.getWeaponDelay(),RobotType.SOLDIER.attackDelay,EPSILON);

        game.waitRounds(1);
        // After one round with zero bytecode, should decrement by new value
        double decrement = 1.0 - (0.3 * Math.pow(0.5,1.5)); // Approx 0.894
        assertEquals(soldierABot.getCoreDelay(),RobotType.SOLDIER.movementDelay-decrement,EPSILON);
        assertEquals(soldierBBot.getWeaponDelay(),RobotType.SOLDIER.attackDelay-decrement,EPSILON);

        game.waitRounds(3);

        // Now use max amount of bytecode
        soldierABot.setBytecodesUsed(RobotType.SOLDIER.bytecodeLimit);
        soldierBBot.setBytecodesUsed(RobotType.SOLDIER.bytecodeLimit);

        //Soldier A moves, soldier B attacks
        game.round((id, rc) -> {
            if (id == soldierA) {
                rc.move(Direction.EAST);
            } else if (id == soldierB) {
                rc.attackLocation(new MapLocation(oX+2,oY+2));
            }
        });

        game.waitRounds(1);
        decrement = 0.7; // Should now only decrease by 0.7 in one turn
        assertEquals(soldierABot.getCoreDelay(),RobotType.SOLDIER.movementDelay-decrement,EPSILON);
        assertEquals(soldierBBot.getWeaponDelay(),RobotType.SOLDIER.attackDelay-decrement,EPSILON);
    }

    /**
     * Test outbreak mechanics.
     */
    @Test
    public void testZombieOutbreak() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 1000);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY + 1, RobotType.ARCHON, Team.A);
        InternalRobot archonBot = game.getBot(archon);
        final int zombie1 = game.spawn(oX, oY, RobotType.STANDARDZOMBIE, Team
                .ZOMBIE);

        // round 0
        game.round((id, rc) -> {
            if (id == zombie1) {
                assertEquals(rc.getHealth(), RobotType.STANDARDZOMBIE
                        .maxHealth, EPSILON);
                rc.attackLocation(new MapLocation(oX, oY + 1));
            }
        });

        assertEquals(archonBot.getHealthLevel(), RobotType.ARCHON.maxHealth -
                RobotType.STANDARDZOMBIE.attackPower, EPSILON);

        game.waitRounds(600);

        // round 601 (multiplier 1.2)
        final int zombie2 = game.spawn(oX + 1, oY, RobotType.RANGEDZOMBIE,
                Team.ZOMBIE);

        game.round((id, rc) -> {
            if (id == zombie2) {
                assertEquals(rc.getHealth(), RobotType.RANGEDZOMBIE
                        .maxHealth * 1.2, EPSILON);
                rc.attackLocation(new MapLocation(oX, oY + 1));
            }
        });

        assertEquals(archonBot.getHealthLevel(), RobotType.ARCHON.maxHealth -
                RobotType.STANDARDZOMBIE.attackPower - RobotType.RANGEDZOMBIE
                .attackPower * 1.2, EPSILON);

        // make sure that a zombie dying leaves the right amount of rubble
        InternalRobot zombie2Bot = game.getBot(zombie2);
        zombie2Bot.takeDamage(zombie2Bot.getHealthLevel());

        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 1, oY)),
                RobotType.RANGEDZOMBIE.maxHealth * 1.2, EPSILON);
    }

    /**
     * Test getting the zombie spawn schedule, and makes sure that modifying
     * this schedule doesn't change the schedule for the actual game.
     */
    @Test
    public void testGetZombieSpawnSchedule() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 1000)
                .withZombieSpawn(100, RobotType.FASTZOMBIE, 30)
                .withZombieSpawn(500, RobotType.RANGEDZOMBIE, 50)
                .withZombieSpawn(500, RobotType.BIGZOMBIE, 4)
                .withZombieSpawn(1000, RobotType.STANDARDZOMBIE, 10);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY + 1, RobotType.ARCHON, Team.A);

        game.round((id, rc) -> {
            if (id == archon) {
                ZombieSpawnSchedule zombieSpawnSchedule = rc
                        .getZombieSpawnSchedule();

                assertArrayEquals(
                        new int[]{100, 500, 1000},
                        zombieSpawnSchedule.getRounds()
                );
                assertArrayEquals(
                        new ZombieCount[]{
                                new ZombieCount(RobotType.FASTZOMBIE, 30)
                        },
                        zombieSpawnSchedule.getScheduleForRound(100)
                );
                assertArrayEquals(
                        new ZombieCount[]{
                                new ZombieCount(RobotType.RANGEDZOMBIE, 50),
                                new ZombieCount(RobotType.BIGZOMBIE, 4)
                        },
                        zombieSpawnSchedule.getScheduleForRound(500)
                );
                assertArrayEquals(
                        new ZombieCount[]{
                                new ZombieCount(RobotType.STANDARDZOMBIE, 10)
                        },
                        zombieSpawnSchedule.getScheduleForRound(1000)
                );

                // now try to modify zombieSpawnSchedule
                zombieSpawnSchedule.add(1500, RobotType.STANDARDZOMBIE, 8);
            }
        });

        // Make sure things didn't change.
        ZombieSpawnSchedule zombieSpawnSchedule = game.getWorld()
                .getGameMap().getZombieSpawnSchedule();
        assertEquals(zombieSpawnSchedule.getRounds().length, 3);
        assertArrayEquals(zombieSpawnSchedule.getRounds(), new int[]{100,
                500, 1000});
    }

    /**
     * Tests activation of neutral bots.
     */
    @Test
    public void testActivation() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 1000);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY + 1, RobotType.ARCHON, Team.A);
        final int neutral = game.spawn(oX, oY, RobotType.SOLDIER, Team.NEUTRAL);

        game.round((id, rc) -> {
            if (id == archon) {
                rc.activate(new MapLocation(oX, oY));
            }
        });

        // make sure that archon now has an ally
        game.round((id, rc) -> {
            if (id == archon) {
                RobotInfo[] nearby = rc.senseNearbyRobots();
                assertEquals(nearby.length, 1);
                assertEquals(nearby[0].location, new MapLocation(oX, oY));
                assertEquals(nearby[0].type, RobotType.SOLDIER);
                assertEquals(nearby[0].team, Team.A);
            }
        });
    }

    /**
     * Makes sure that you can't build stuff that you're not supposed to build.
     */
    @Test
    public void testArchonCantBuildZombie() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 1000);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int den = game.spawn(oX + 5, oY + 5, RobotType.ZOMBIEDEN, Team
                .ZOMBIE);

        game.round((id, rc) -> {
            if (id == archon) {
                boolean exception = false;
                try {
                    rc.build(Direction.SOUTH_EAST, RobotType.RANGEDZOMBIE);
                } catch (GameActionException e) {
                    exception = true;
                }
                assertTrue(exception);
            } else if (id == den) {
                rc.build(Direction.SOUTH_EAST, RobotType.RANGEDZOMBIE);
            }
        });
    }

    /**
     * Test signaling behavior
     */
    @Test
    public void testSignaling() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 1000);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int soldier = game.spawn(oX, oY + 4, RobotType.SOLDIER, Team.B);
        final int guard = game.spawn(oX, oY + 5, RobotType.GUARD, Team.B);

        game.round((id, rc) -> {
            if (id == archon) {
                rc.broadcastMessageSignal(123, 456, 24);
                assertEquals(rc.getCoreDelay(), GameConstants
                        .BROADCAST_BASE_DELAY_INCREASE, EPSILON);
                assertEquals(rc.getWeaponDelay(), GameConstants
                        .BROADCAST_BASE_DELAY_INCREASE, EPSILON);
            } else if (id == soldier) {
                rc.broadcastSignal(2);
            } else if (id == guard) {
                rc.broadcastSignal(10000);
                double x = 10000.0 / RobotType.GUARD.sensorRadiusSquared - 2;
                assertEquals(rc.getCoreDelay(), GameConstants
                        .BROADCAST_BASE_DELAY_INCREASE + x * GameConstants
                        .BROADCAST_ADDITIONAL_DELAY_INCREASE, EPSILON);
                assertEquals(rc.getWeaponDelay(), GameConstants
                        .BROADCAST_BASE_DELAY_INCREASE + x * GameConstants
                        .BROADCAST_ADDITIONAL_DELAY_INCREASE, EPSILON);
            }
        });

        // verify messages
        game.round((id, rc) -> {
            if (id == archon) {
                Signal[] queue = rc.emptySignalQueue();
                assertEquals(queue.length, 1);
                assertEquals(queue[0].getMessage(), null);
                assertEquals(queue[0].getRobotID(), guard);
                assertEquals(queue[0].getLocation(), new MapLocation(oX, oY +
                        5));
                assertEquals(queue[0].getTeam(), Team.B);
            } else if (id == soldier) {
                Signal first = rc.readSignal();
                Signal second = rc.readSignal();
                Signal third = rc.readSignal();
                assertArrayEquals(first.getMessage(), new int[]{123, 456});
                assertEquals(first.getRobotID(), archon);
                assertEquals(first.getLocation(), new MapLocation(oX, oY));
                assertEquals(first.getTeam(), Team.A);
                assertArrayEquals(second.getMessage(), null);
                assertEquals(second.getRobotID(), guard);
                assertEquals(second.getLocation(), new MapLocation(oX, oY + 5));
                assertEquals(second.getTeam(), Team.B);
                assertEquals(third, null);
            } else if (id == guard) {
                Signal[] queue = rc.emptySignalQueue();
                assertEquals(queue.length, 1);
                assertEquals(queue[0].getMessage(), null);
                assertEquals(queue[0].getTeam(), Team.B);
                assertEquals(queue[0].getLocation(), new MapLocation(oX, oY +
                        4));
                assertEquals(queue[0].getRobotID(), soldier);
            }
        });
    }
    
    /**
     * Test case to ensure issue #174 in battlecode-server is fixed
     * (test fails before exploit is fixed)
     */
    @Test
    public void testSignalExploit() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 1000);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int soldier = game.spawn(oX, oY + 1, RobotType.SOLDIER, Team.B);
        final int guard = game.spawn(oX, oY + 2, RobotType.GUARD, Team.B);
        
        // Fist, archon sends a message
        game.round((id, rc) -> {
            if (id == archon) {
                rc.broadcastMessageSignal(123, 456, 24);
            } else if (id == soldier) {
            } else if (id == guard) {
            }
        });
        
        // Guard modifies message
        game.round((id, rc) -> {
            if (id == archon) {
            } else if (id == soldier) {
            } else if (id == guard) {
                Signal unmodified = rc.readSignal();
                int data[] = unmodified.getMessage();
                data[0] = 1337;
                data[1] = 42069;
            }
        });
        
        // Soldier receives original message
        game.round((id, rc) -> {
            if (id == archon) {
            } else if (id == soldier) {
                Signal unmodified = rc.readSignal();
                int data[] = unmodified.getMessage();
                assertArrayEquals(data, new int[]{123, 456});
            } else if (id == guard) {
            }
        });
    }
    
    @Test
    public void testSignalLimits() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 1000);

        GameMap map = mapGen.getMap("test");

        TestGame game = new TestGame(map);

        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int archon = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int soldier = game.spawn(oX, oY + 1, RobotType.SOLDIER, Team.B);
        
        // Archon sends max amount in one turn
        game.round((id, rc) -> {
            if (id == archon) {
                boolean simpleException = false;
                boolean messageException = false;
                for(int i = 0; i < GameConstants.MESSAGE_SIGNALS_PER_TURN+1; i++) {
                    try {
                        rc.broadcastMessageSignal(123, 456, 24);
                        if (i < GameConstants.MESSAGE_SIGNALS_PER_TURN) {
                            assertEquals(rc.getMessageSignalCount(), i + 1);
                        }
                    } catch(Exception e) {
                        messageException = true;
                    }
                    try {
                        rc.broadcastSignal(24);
                    } catch(Exception e) {
                        simpleException = true;
                    }
                }
                assertTrue(simpleException);
                assertTrue(messageException);
                assertEquals(rc.getMessageSignalCount(), GameConstants
                        .MESSAGE_SIGNALS_PER_TURN);
                assertEquals(rc.getBasicSignalCount(), GameConstants
                        .BASIC_SIGNALS_PER_TURN);
            } else if (id == soldier) {
            }
        });
        
        // Soldier receives 25 signals
        game.round((id, rc) -> {
            if (id == archon) {
                assertEquals(rc.getBasicSignalCount(), 0);
                assertEquals(rc.getMessageSignalCount(), 0);
            } else if (id == soldier) {
                Signal[] signals = rc.emptySignalQueue();
                assertEquals(signals.length,GameConstants.BASIC_SIGNALS_PER_TURN+
                        GameConstants.MESSAGE_SIGNALS_PER_TURN);
            }
        });
        
        // Now archon sends 1020 signals
        for(int turn = 0; turn < 1020/20; turn++) {
            final int turnToPass = turn; // Necessary to access inside lambda
            game.round((id, rc) -> {
                if (id == archon) {
                    for(int i = 0; i < 20; i++) {
                        rc.broadcastMessageSignal(i, turnToPass, 24);
                    }
                } else if (id == soldier) {
                }
            });
        }
        
        // Soldier can only see 1000 most recent
        game.round((id, rc) -> {
            if (id == archon) {
            } else if (id == soldier) {
                Signal[] signals = rc.emptySignalQueue();
                assertEquals(signals.length,GameConstants.SIGNAL_QUEUE_MAX_SIZE);
                assertEquals(signals[0].getMessage()[0],0);
                assertEquals(signals[0].getMessage()[1], 1);
            }
        });
    }
    
    /**
     * Tests the canSense(InternalRobot) method and the senseHostileRobots() method
     */
    @Test
    public void testRobotSensing() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int soldier1 = game.spawn(oX, oY, RobotType.SOLDIER,Team.A);
        final int soldier2 = game.spawn(oX + 3, oY + 3, RobotType.SOLDIER, Team.A);
        final int soldier3 = game.spawn(oX+6, oY+6, RobotType.SOLDIER,Team.B);
        final int soldier4 = game.spawn(oX + 10, oY + 10, RobotType.SOLDIER, Team.A);
        final int zombie = game.spawn(oX+4, oY+2, RobotType.STANDARDZOMBIE, Team.ZOMBIE);
        InternalRobot soldier1Bot = game.getBot(soldier1);
        InternalRobot soldier2Bot = game.getBot(soldier2);
        InternalRobot soldier3Bot = game.getBot(soldier3);
        
        game.round((id, rc) -> {
            if (id == soldier1) {
                assertTrue(rc.canSenseRobot(soldier2));
                assertFalse(rc.canSenseRobot(soldier3));
                RobotInfo[] hostiles = rc.senseHostileRobots(soldier1Bot
                        .getLocation(), -1);
                assertEquals(hostiles.length, 1);
                RobotInfo[] allRobots = rc.senseNearbyRobots();
                assertEquals(allRobots.length, 2);
            } else if (id == soldier2) {
                assertTrue(rc.canSenseRobot(soldier1));
                assertTrue(rc.canSenseRobot(soldier3));
                RobotInfo[] hostiles = rc.senseHostileRobots(soldier2Bot
                        .getLocation(), 2);
                assertEquals(hostiles.length, 1);
                hostiles = rc.senseHostileRobots(soldier2Bot.getLocation(), -1);
                assertEquals(hostiles.length, 2);
                RobotInfo[] allRobots = rc.senseNearbyRobots();
                assertEquals(allRobots.length, 3);
            } else if (id == soldier3) {
                assertTrue(rc.canSenseRobot(soldier2));
                assertFalse(rc.canSenseRobot(soldier1));
                RobotInfo[] allRobots = rc.senseNearbyRobots();
                assertEquals(allRobots.length, 2);
            } else if (id == zombie) {
            }
        });
    }

    /**
     * Makes sure a turret can't attack things within 5 units.
     */
    @Test
    public void testTurretAttackRange() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int turret = game.spawn(oX, oY, RobotType.TURRET, Team.A);

        game.round((id, rc) -> {
            if (id == turret) {
                // 5 is bad
                assertFalse(rc.canAttackLocation(new MapLocation(oX + 2, oY +
                        1)));
                // 6 is OK
                assertTrue(rc.canAttackLocation(new MapLocation(oX + 6, oY)));

                boolean exception = false;
                try {
                    rc.attackLocation(new MapLocation(oX + 2, oY + 1));
                } catch (GameActionException e) {
                    exception = true;
                }
                assertTrue(exception);
            }
        });
    }

    /**
     * Makes sure that if you spawn on a parts location, you pick it up.
     */
    @Test
    public void testSpawningOnParts() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100)
                .withParts(0, 0, 100)
                .withParts(0, 1, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int bot1 = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int bot2 = game.spawn(oX, oY + 1, RobotType.ARCHON, Team.NEUTRAL);

        game.round((id, rc) -> {
        });

        assertEquals(game.getWorld().getParts(new MapLocation(oX, oY)), 0,
                EPSILON);
        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT + GameConstants.ARCHON_PART_INCOME -
                GameConstants.PART_INCOME_UNIT_PENALTY + 100, EPSILON);
        assertEquals(game.getWorld().getParts(new MapLocation(oX, oY + 1)), 100,
                EPSILON);
    }

    /**
     * Makes sure that parts income is dependent on how many units you have.
     */
    @Test
    public void testPartIncome() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int bot1 = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int bot2 = game.spawn(oX, oY + 1, RobotType.ARCHON, Team.A);
        final int bot3 = game.spawn(oX, oY + 2, RobotType.SOLDIER, Team.A);
        final int bot4 = game.spawn(oX, oY + 3, RobotType.TTM, Team.B);

        game.round((id, rc) -> {
        });

        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT + GameConstants.ARCHON_PART_INCOME - 3
                * GameConstants.PART_INCOME_UNIT_PENALTY, EPSILON);
        assertEquals(game.getWorld().resources(Team.B), GameConstants
                .PARTS_INITIAL_AMOUNT + GameConstants.ARCHON_PART_INCOME - 1
                * GameConstants.PART_INCOME_UNIT_PENALTY, EPSILON);
        assertEquals(3, game.getWorld().getRobotCount(Team.A));

        InternalRobot bot3Bot = game.getBot(bot3);
        bot3Bot.takeDamage(bot3Bot.getHealthLevel());
        InternalRobot bot4Bot = game.getBot(bot4);
        bot4Bot.takeDamage(bot4Bot.getHealthLevel());
        game.round((id, rc) -> {
        });

        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT + 2 * GameConstants.ARCHON_PART_INCOME - 5
                * GameConstants.PART_INCOME_UNIT_PENALTY, EPSILON);
        assertEquals(game.getWorld().resources(Team.B), GameConstants
                .PARTS_INITIAL_AMOUNT + 2 * GameConstants.ARCHON_PART_INCOME - 1
                * GameConstants.PART_INCOME_UNIT_PENALTY, EPSILON);

        assertEquals(2, game.getWorld().getRobotCount(Team.A));

        game.round((id, rc) -> {
            if (id == bot1) {
                rc.build(Direction.EAST, RobotType.TURRET);
            }
        });

        assertEquals(game.getWorld().resources(Team.A), GameConstants
                .PARTS_INITIAL_AMOUNT + 3 * GameConstants.ARCHON_PART_INCOME - 8
                * GameConstants.PART_INCOME_UNIT_PENALTY - RobotType.TURRET
                .partCost, EPSILON);
        assertEquals(game.getWorld().resources(Team.B), GameConstants
                .PARTS_INITIAL_AMOUNT + 3 * GameConstants.ARCHON_PART_INCOME - 1
                * GameConstants.PART_INCOME_UNIT_PENALTY, EPSILON);
        assertEquals(3, game.getWorld().getRobotCount(Team.A));
    }

    /**
     * Verifies guard damage reduction.
     */
    @Test
    public void testGuardDamageReduction() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int bot1 = game.spawn(oX, oY, RobotType.BIGZOMBIE, Team.ZOMBIE);
        final int bot2 = game.spawn(oX + 1, oY, RobotType.SOLDIER, Team.A);
        final int bot3 = game.spawn(oX, oY + 1, RobotType.GUARD, Team.B);
        InternalRobot guard = game.getBot(bot3);

        game.round((id, rc) -> {
            if (id == bot2) {
                rc.attackLocation(new MapLocation(oX, oY + 1));
            }
        });

        assertEquals(guard.getHealthLevel(), guard.getMaxHealth() - RobotType
                .SOLDIER.attackPower, EPSILON);

        game.round((id, rc) -> {
            if (id == bot1) {
                rc.attackLocation(new MapLocation(oX, oY + 1));
            }
        });

        assertEquals(guard.getHealthLevel(), guard.getMaxHealth() - RobotType
                .SOLDIER.attackPower - RobotType.BIGZOMBIE.attackPower +
                GameConstants.GUARD_DAMAGE_REDUCTION, EPSILON);
    }

    /**
     * Make sure an error is thrown if you try to clear rubble on an off map
     * location.
     */
    @Test
    public void testClearRubbleOffMap() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int bot1 = game.spawn(oX, oY, RobotType.ARCHON, Team.A);

        game.round((id, rc) -> {
            if (id == bot1) {
                boolean exception = false;
                try {
                    rc.clearRubble(Direction.NORTH);
                } catch (GameActionException e) {
                    exception = true;
                }
                assertTrue(exception);
            }
        });
    }

    /**
     * If both teams lose their last archon in the same round (but not
     * necessarily the same turn), the one who loses the archon last should win.
     */
    @Test
    public void testDoubleArchonDeath() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int bot1 = game.spawn(oX, oY, RobotType.ARCHON, Team.A);
        final int bot2 = game.spawn(oX, oY + 1, RobotType.ARCHON, Team.B);
        final int bot3 = game.spawn(oX + 1, oY, RobotType.SOLDIER, Team.A);
        final int bot4 = game.spawn(oX + 1, oY + 1, RobotType.SOLDIER, Team.B);

        InternalRobot bot1Bot = game.getBot(bot1);
        InternalRobot bot2Bot = game.getBot(bot2);
        bot1Bot.takeDamage(RobotType.ARCHON.maxHealth - 1);
        bot2Bot.takeDamage(RobotType.ARCHON.maxHealth - 1);

        game.round((id, rc) -> {
            if (id == bot3) {
                rc.attackLocation(new MapLocation(oX, oY));
            } else if (id == bot4) {
                rc.attackLocation(new MapLocation(oX, oY + 1));
            }
        });

        // Make sure both archons died
        assertEquals(bot1Bot.getHealthLevel(), -3, EPSILON);
        assertEquals(bot2Bot.getHealthLevel(), -3, EPSILON);

        // Make sure Team B was the winner
        assertEquals(game.getWorld().getWinner(), Team.B);
    }

    /**
     * Test getting initial archon locations.
     */
    @Test
    public void testGetInitialArchonLocations() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100)
                .withRobot(RobotType.ARCHON, Team.A, 0, 0)
                .withRobot(RobotType.SOLDIER, Team.A, 1, 1)
                .withRobot(RobotType.ARCHON, Team.B, 3, 3)
                .withRobot(RobotType.GUARD, Team.B, 4, 4)
                .withRobot(RobotType.ARCHON, Team.NEUTRAL, 5, 5)
                .withRobot(RobotType.SCOUT, Team.A, 0, 1)
                .withRobot(RobotType.ARCHON, Team.B, 2, 2)
                .withRobot(RobotType.ARCHON, Team.B, 2, 3);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int bot1 = game.spawn(oX + 6, oY + 6, RobotType.ARCHON,
                Team.A);

        game.round((id, rc) -> {
            if (id == bot1) {
                MapLocation[] locsA = rc.getInitialArchonLocations(Team.A);
                MapLocation[] locsB = rc.getInitialArchonLocations(Team.B);
                MapLocation[] locsN = rc.getInitialArchonLocations(Team
                        .NEUTRAL);
                MapLocation[] locsZ = rc.getInitialArchonLocations(Team
                        .ZOMBIE);

                assertEquals(locsZ.length, 0);
                assertEquals(locsN.length, 0);

                assertEquals(locsA.length, 1);
                assertEquals(locsB.length, 3);
                assertEquals(locsA[0], new MapLocation(oX, oY));
                assertEquals(locsB[0], new MapLocation(oX + 2, oY + 2));
                assertEquals(locsB[1], new MapLocation(oX + 2, oY + 3));
                assertEquals(locsB[2], new MapLocation(oX + 3, oY + 3));
            }
        });
    }

    /**
     * Deaths by turret should only produce 1/3 rubble. Normal deaths should
     * produce full rubble. Death by activation should produce no rubble.
     */
    @Test
    public void testDeathCauses() throws GameActionException {
        TestMapGenerator mapGen = new TestMapGenerator(10, 10, 100);
        GameMap map = mapGen.getMap("test");
        TestGame game = new TestGame(map);
        int oX = game.getOriginX();
        int oY = game.getOriginY();
        final int turret = game.spawn(oX + 5, oY + 5, RobotType.TURRET, Team.A);
        final int soldier = game.spawn(oX, oY + 1, RobotType.SOLDIER, Team
                .A);
        final int archon = game.spawn(oX + 1, oY + 3, RobotType.ARCHON, Team.A);
        final int bot1 = game.spawn(oX + 1, oY + 1, RobotType.SOLDIER, Team.B);
        final int bot2 = game.spawn(oX + 2, oY + 1, RobotType.SOLDIER, Team.B);
        final int bot3 = game.spawn(oX + 1, oY + 2, RobotType.SOLDIER, Team
                .NEUTRAL);

        InternalRobot bot1Bot = game.getBot(bot1);
        InternalRobot bot2Bot = game.getBot(bot2);
        InternalRobot bot3Bot = game.getBot(bot3);
        bot1Bot.takeDamage(RobotType.SOLDIER.maxHealth - 1);
        bot2Bot.takeDamage(RobotType.SOLDIER.maxHealth - 1);
        bot3Bot.takeDamage(RobotType.SOLDIER.maxHealth - 1);

        game.round((id, rc) -> {
            if (id == turret) {
                rc.attackLocation(new MapLocation(oX + 1, oY + 1));
            } else if (id == soldier) {
                rc.attackLocation(new MapLocation(oX + 2, oY + 1));
            } else if (id == archon) {
                rc.activate(new MapLocation(oX + 1, oY + 2));
            }
        });

        // Death by turret should leave 1/3 rubble.
        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 1, oY +
                1)), RobotType.SOLDIER.maxHealth * GameConstants
                .RUBBLE_FROM_TURRET_FACTOR, EPSILON);

        // Death by normal attack should leave 100% rubble.
        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 2, oY +
                1)), RobotType.SOLDIER.maxHealth, EPSILON);

        // Death by activation should leave 0 rubble.
        assertEquals(game.getWorld().getRubble(new MapLocation(oX + 1, oY +
                2)), 0, EPSILON);
    }
}
