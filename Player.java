package pb.g2;

import pb.sim.Point;
import pb.sim.Orbit;
import pb.sim.Asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class Player implements pb.sim.Player {

	// current time, time limit
	private long time = -1;
	private long time_limit = -1;

	private int number_of_asteroids;

	private long next_push = 0;

	// print orbital information
	public void init(Asteroid[] asteroids, long time_limit)
	{
		if (Orbit.dt() != 24 * 60 * 60)
			throw new IllegalStateException("Time quantum is not a day");
		this.time_limit = time_limit;
		this.number_of_asteroids = asteroids.length;
	}

	// try to push asteroid
	public void play(Asteroid[] asteroids,
	                 double[] energy, double[] direction) {
        time++;
        int n = asteroids.length;

        if (asteroids.length < number_of_asteroids) {
            System.out.println("A collision just occurred at time " + time);
            // Check for non-circular orbit
            for (int i = 0; i < asteroids.length; i++) {
                if (Math.abs(asteroids[i].orbit.a - asteroids[i].orbit.b) > 10e-6) {
                    // // Correct for non-circular orbit
                    Push push = Hohmann.generateCorrection(asteroids[i], time);
                    energy[i] = push.energy;
                    direction[i] = push.direction;
                }
            }

            next_push = 0; // Void
            number_of_asteroids = asteroids.length;
            return;
        }

        if (time <= next_push) return;

        // Pick asteroid to push to
        // Sort asteroids in order of how attractive they are to become nucleus
        ArrayList<ComparableAsteroid> sorted_asteroids = new ArrayList<ComparableAsteroid>();
        Point asteroid_position = new Point();
        Point sun = new Point(0, 0);
        for (int i = 0; i < n; i++) {
            asteroids[i].orbit.positionAt(time - asteroids[i].epoch, asteroid_position);
            sorted_asteroids.add(new ComparableAsteroid(i, Point.distance(sun, asteroid_position), asteroids[i].mass));
        }
        Collections.sort(sorted_asteroids);

        // Get nucleus asteroid to which we will push all other asteroids
        int nucleus_index = sorted_asteroids.get(n - 1).index;
        Asteroid nucleus = asteroids[nucleus_index];

        // Of all remaining asteroids, find the one with lowest energy push
        Push min_push = new Push(null, 0, Long.MAX_VALUE, 0, 0);
        long time_of_collision = -1;
        for (int i = n - 2; i >= 0; i--) {
            int curr_asteroid_index = sorted_asteroids.get(i).index;
            Asteroid curr_asteroid = asteroids[curr_asteroid_index];

            // Ignore asteroids with elliptical orbits
            if (curr_asteroid.orbit.a != curr_asteroid.orbit.b) {
                continue;
            }

            Push push = Hohmann.generatePush(curr_asteroid, nucleus, time);
            Asteroid pushed_asteroid = Asteroid.push(min_push.asteroid, time, min_push.energy, min_push.direction);
            time_of_collision = CollisionChecker.checkCollision(pushed_asteroid, nucleus, min_push.expected_collision_time,
                    time, time_limit);
            if (push.energy < min_push.energy) {
                min_push = push;
            }
        }

        if (time_of_collision != -1) {
            energy[min_push.index] = min_push.energy;
            direction[min_push.index] = min_push.direction;
            next_push = time_of_collision;
            return;
        }

        // ¯\_(ツ)_/¯
        giveUpAndTryEverything(nucleus_index, asteroids, energy, direction);
    }


    /**
     * Worst case: If we could not collide anything into the nucleus,
     * try to collide any two asteroids into each other
     */
    public void giveUpAndTryEverything(int nucleus_index, Asteroid[] asteroids, double[] energy, double[] direction) {
        int n = asteroids.length;
        for (int i = 0; i < n; i++) {
            if (i == nucleus_index) {
                continue;
            }
            for (int j = 0; j < n; j++) {
                if (j == nucleus_index) {
                    continue;
                }

                Push push = Hohmann.generatePush(asteroids[i], asteroids[j], time);
                Asteroid pushed_asteroid = Asteroid.push(asteroids[i], time, push.energy, push.direction);

                long time_of_collision = CollisionChecker.checkCollision(pushed_asteroid, asteroids[j], push.expected_collision_time,
                        time, time_limit);
                if (time_of_collision != -1) {
                    energy[i] = push.energy;
                    direction[i] = push.direction;
                    next_push = time_of_collision;
                    return;
                }
            }
        }
    }
}
