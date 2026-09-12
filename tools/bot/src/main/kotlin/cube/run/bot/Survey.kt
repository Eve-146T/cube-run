package cube.run.bot

import java.io.*
import java.util.concurrent.Executors
import java.util.Properties
import java.security.MessageDigest

/** Host-side exhaustive *coverage* of the fixture set; each individual search is bounded. */
fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--self-test"))) { selfTest(); return }
    require(args.size >= 2) { "survey <courses.bin> <output-dir> [speeds CSV] [beam] [threads]" }
    val courses = DataInputStream(File(args[0]).inputStream().buffered()).use(CourseFile::read)
    val output = File(args[1]).apply { mkdirs() }
    val speeds = args.getOrElse(2) { "12.4,21.6,27,30,40,52.5,65,80,90" }.split(',').map(String::toFloat)
    val beam = args.getOrElse(3) { "96" }.toInt()
    val metadata = Properties().apply {
        setProperty("courseCount", courses.size.toString())
        setProperty("sectionCount", courses.map { it.id }.distinct().size.toString())
        setProperty("expectedTrials", (courses.size * speeds.size * 3).toString())
        setProperty("speeds", speeds.joinToString(","))
        setProperty("beam", beam.toString())
        setProperty("physicsHz", "60")
        setProperty("objective", "survival-only")
        setProperty("fixtureSha256", MessageDigest.getInstance("SHA-256").digest(File(args[0]).readBytes()).joinToString("") { "%02x".format(it) })
        setProperty("complete", "false")
    }
    fun saveMetadata() { File(output, "run.properties").outputStream().use { metadata.store(it, "Local bot survey") } }
    saveMetadata()
    val workers = Executors.newFixedThreadPool(args.getOrElse(4) { "4" }.toInt())
    val report = File(output, "trials.csv").bufferedWriter()
    report.appendLine("id,name,tier,seed,mirror,entry,phase,speed,profile,solved,reached_frames,total_frames,gestures,peak_per_second,reward,jitter_1_of_12,jitter_3_of_12,expanded,beam")
    try {
        val tasks = courses.map { course -> workers.submit<Unit> {
            for (speed in speeds) {
                // Ratings ask what survival requires. Optional coin detours must not
                // inflate skill; the live controller retains reward-seeking behavior.
                val survivalCourse = course.copy(rows = course.rows.map { it.copy(goodies = emptyList()) })
                val timeline = Timeline(survivalCourse, speed)
                for ((profile, cadence) in listOf("perfect" to 1, "pro-model" to 8, "relaxed-model" to 12)) {
                    var plan = Planner(beam, cadence).solve(timeline)
                    var usedBeam = beam
                    if (!plan.survived) { usedBeam = beam * 3; plan = Planner(usedBeam, cadence).solve(timeline) }
                    plan = centerTiming(timeline, plan, cadence)
                    if (plan.survived) check(replay(Timeline(course, speed, conservative = false), plan.actions)) { "Plan failed literal replay" }
                    val j1 = if (plan.survived) jitterSurvival(timeline, plan, 1) else 0
                    val j3 = if (plan.survived) jitterSurvival(timeline, plan, 3) else 0
                    val key = "${course.id}_${course.seed}_${course.mirror}_${course.entry}_${course.phase}_${speed}_$profile"
                    if (plan.survived) File(output, "$key.actions").writeText(plan.actions.joinToString(","))
                    val row = listOf(course.id, course.name, course.tier, course.seed, course.mirror, course.entry, course.phase,
                        speed, profile, plan.survived, plan.reachedFrame, timeline.frames.size, plan.actions.count { it != 0 },
                        plan.peakGesturesPerSecond, plan.rewards, j1, j3, plan.expanded, usedBeam).joinToString(",")
                    synchronized(report) { report.appendLine(row); report.flush() }
                }
            }
            println("finished ${course.id} ${course.name} seed=${course.seed} mirror=${course.mirror} entry=${course.entry} phase=${course.phase}")
        } }
        for (task in tasks) task.get()
        metadata.setProperty("complete", "true"); saveMetadata()
    } finally { workers.shutdownNow(); report.close() }
}
