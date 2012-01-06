package battlecode.common;

/**
 * Struct that stores basic information that was 'sensed' of another Robot. This
 * info is ephemeral and there is no guarantee any of it will remain the same
 * between rounds.
 * 
 * @author Teh Devs
 */
public class RobotInfo {

    /** The robot that was sensed. */
    public final Robot robot;
    /** The location of this Robot. */
    public final MapLocation location;
    /** The energon of this Robot. */
    public final double energon;
	/** The flux of this Robot. */
	public final double flux;
    /** The direction this Robot is facing. */
    public final Direction direction;
	/** The type of this Robot. */
    public final RobotType type;
	/** The team of this Robot. */
	public final Team team;
	/** <code>true</code> if this robot is scheduled to regenerate
	 * at the beginning of its next turn. */
   	public boolean regen;

    public RobotInfo(Robot robot, MapLocation location,
            double hitpoints, double flux, Direction direction,
            RobotType type, Team team, boolean regen) {
        super();
        this.robot = robot;
        this.location = location;
        this.energon = hitpoints;
		this.flux = flux;
        this.direction = direction;
        this.type = type;
		this.team = team;
		this.regen = regen;
    }

    public int hashCode() {
        return robot.hashCode();
    }
}
