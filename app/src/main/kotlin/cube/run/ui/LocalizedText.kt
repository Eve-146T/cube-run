package cube.run.ui

import android.content.Context
import cube.run.R

/** Resolve stable model labels only when presenting them; save keys and simulation names stay unchanged. */
fun Context.gameText(stableLabel: String): String = when (stableLabel) {
    "Classic" -> getString(R.string.game_classic)
    "Neon" -> getString(R.string.game_neon)
    "Lava" -> getString(R.string.game_lava)
    "Ice" -> getString(R.string.game_ice)
    "Void" -> getString(R.string.game_void)
    "Plasma" -> getString(R.string.game_plasma)
    "Gold" -> getString(R.string.game_gold)
    "Rainbow" -> getString(R.string.game_rainbow)
    "Mint" -> getString(R.string.game_mint)
    "Rose" -> getString(R.string.game_rose)
    "Ocean" -> getString(R.string.game_ocean)
    "Toxic" -> getString(R.string.game_toxic)
    "Sunset" -> getString(R.string.game_sunset)
    "Ghost" -> getString(R.string.game_ghost)
    "Strobe" -> getString(R.string.game_strobe)
    "Coal" -> getString(R.string.game_coal)
    "Gambler" -> getString(R.string.game_gambler)
    "Cloud" -> getString(R.string.game_cloud)
    "Black void" -> getString(R.string.game_black_void)
    "Event horizon" -> getString(R.string.game_event_horizon)
    "Afterimage" -> getString(R.string.game_afterimage)
    "Bubblegum" -> getString(R.string.game_bubblegum)
    "Lemon" -> getString(R.string.game_lemon)
    "Candy" -> getString(R.string.game_candy)
    "Galaxy" -> getString(R.string.game_galaxy)
    "Inferno" -> getString(R.string.game_inferno)
    "Glacier" -> getString(R.string.game_glacier)
    "Eclipse" -> getString(R.string.game_eclipse)
    "Speedy cube" -> getString(R.string.game_speedy_cube)
    "Soap" -> getString(R.string.game_soap)
    "Sparks" -> getString(R.string.game_sparks)
    "Flame" -> getString(R.string.game_flame)
    "Frost" -> getString(R.string.game_frost)
    "Smoke" -> getString(R.string.game_smoke)
    "Confetti" -> getString(R.string.game_confetti)
    "Stardust" -> getString(R.string.game_stardust)
    "Bubbles" -> getString(R.string.game_bubbles)
    "Pixie" -> getString(R.string.game_pixie)
    "No trail" -> getString(R.string.game_no_trail)
    "Ember shards" -> getString(R.string.game_ember_shards)
    "Frost shards" -> getString(R.string.game_frost_shards)
    "Void shards" -> getString(R.string.game_void_shards)
    "CUBE" -> getString(R.string.game_cube)
    "BUBBLE" -> getString(R.string.game_bubble)
    "TRAIL" -> getString(R.string.game_trail)
    "Phase" -> getString(R.string.game_phase)
    "Slip through one obstacle each run!" -> getString(R.string.ability_phase_detail)
    "Allows you to phase through one obstacle per run." -> getString(R.string.game_allows_you_to_phase_through_one_obstacle_per_run)
    "1 / RUN" -> getString(R.string.game_1_run)
    "Speed" -> getString(R.string.game_speed)
    "Your cube runs 30% faster!" -> getString(R.string.ability_speed_detail)
    "Makes you 30% faster." -> getString(R.string.game_makes_you_30_faster)
    "+30%" -> getString(R.string.game_30)
    "Bubble saver" -> getString(R.string.game_bubble_saver)
    "35% chance to keep your bubble when you use it!" -> getString(R.string.ability_bubble_saver_detail)
    "Midas Little Toe" -> getString(R.string.ability_midas)
    "Coins you collect are worth 20% more!" -> getString(R.string.ability_midas_detail)
    "Zappy" -> getString(R.string.ability_zappy)
    "Teleport between lanes in a flash!" -> getString(R.string.ability_zappy_detail)
    "Power stretch" -> getString(R.string.ability_power_stretch)
    "Your power-ups last 25% longer!" -> getString(R.string.ability_power_stretch_detail)
    "Quick bubble" -> getString(R.string.ability_quick_bubble)
    "Pop another bubble 30% sooner!" -> getString(R.string.ability_quick_bubble_detail)
    "Turns every coin into coal. Coal is worthless." -> getString(R.string.ability_coal_detail)
    "Lottery" -> getString(R.string.ability_lottery)
    "All the coins you collect are spent on playing the Lottery! The jackpot is 250k coins. Each coin of value has a 1 in 100,000 chance; each mystery box has a 1.3% chance." -> getString(R.string.ability_lottery_detail)
    "Floaty" -> getString(R.string.ability_floaty)
    "Your cube becomes floaty!" -> getString(R.string.ability_floaty_detail)
    "Double jump" -> getString(R.string.ability_double_jump)
    "Jump twice while your bubble is active!" -> getString(R.string.ability_double_jump_detail)
    "Long bubble" -> getString(R.string.ability_long_bubble)
    "Your bubble lasts 30% longer!" -> getString(R.string.ability_long_bubble_detail)
    "35% chance to not consume a bubble." -> getString(R.string.game_35_chance_to_not_consume_a_bubble)
    "35%" -> getString(R.string.game_35)
    "Bubble" -> getString(R.string.game_bubble)
    "Magnet" -> getString(R.string.game_magnet)
    "2× score" -> getString(R.string.game_2_score)
    "Jetpack" -> getString(R.string.game_jetpack)
    "Safe start" -> getString(R.string.game_safe_start)
    "Rich coins" -> getString(R.string.game_rich_coins)
    "Portal luck" -> getString(R.string.game_portal_luck)
    "Lucky boxes" -> getString(R.string.game_lucky_boxes)
    "Even faster starts" -> getString(R.string.game_even_faster_starts)
    "Candy Fields" -> getString(R.string.game_candy_fields)
    "Neon City" -> getString(R.string.game_neon_city)
    "Lava Caves" -> getString(R.string.game_lava_caves)
    "Frost Peaks" -> getString(R.string.game_frost_peaks)
    "Sunset Dunes" -> getString(R.string.game_sunset_dunes)
    "Deep Space" -> getString(R.string.game_deep_space)
    "Wide Open" -> getString(R.string.game_wide_open)
    "Rollercoaster" -> getString(R.string.game_rollercoaster)
    "Zero-G" -> getString(R.string.game_zero_g)
    "Kaleidoscope" -> getString(R.string.game_kaleidoscope)
    "Five lanes. Room to breathe, room to lose yourself." -> getString(R.string.game_five_lanes_room_to_breathe_room_to_lose_yourself)
    "The road rises and falls. Hold on." -> getString(R.string.game_the_road_rises_and_falls_hold_on)
    "The lanes drift apart and you float between them." -> getString(R.string.game_the_lanes_drift_apart_and_you_float_between_them)
    "Nothing holds its colour. Nothing holds still." -> getString(R.string.game_nothing_holds_its_colour_nothing_holds_still)
    "FIRST STEPS" -> getString(R.string.section_first_steps)
    "WEAVE" -> getString(R.string.section_weave)
    "HOP" -> getString(R.string.section_hop)
    "HURDLES" -> getString(R.string.section_hurdles)
    "SPRINGBOARD" -> getString(R.string.section_springboard)
    "SLALOM" -> getString(R.string.section_slalom)
    "LEAP & WEAVE" -> getString(R.string.section_leap_weave)
    "CLOSING GATES" -> getString(R.string.section_closing_gates)
    "DUCK & DODGE" -> getString(R.string.section_duck_dodge)
    "STAIRCASE" -> getString(R.string.section_staircase)
    "SKYLIGHTS" -> getString(R.string.section_skylights)
    "TAR PITS" -> getString(R.string.section_tar_pits)
    "PUMP HOUSE" -> getString(R.string.section_pump_house)
    "HIGH ROAD" -> getString(R.string.section_high_road)
    "RAMP UP" -> getString(R.string.section_ramp_up)
    "GOLD RUSH" -> getString(R.string.section_gold_rush)
    "LOW BRIDGE" -> getString(R.string.section_low_bridge)
    "SWING SET" -> getString(R.string.section_swing_set)
    "BOUNCE HOUSE" -> getString(R.string.section_bounce_house)
    "HIGH JUMP" -> getString(R.string.section_high_jump)
    "GAUNTLET" -> getString(R.string.section_gauntlet)
    "STORM" -> getString(R.string.section_storm)
    "TRAPS" -> getString(R.string.section_traps)
    "THE WAVE" -> getString(R.string.section_the_wave)
    "TUNNEL" -> getString(R.string.section_tunnel)
    "PEEKABOO" -> getString(R.string.section_peekaboo)
    "STOMP" -> getString(R.string.section_stomp)
    "SWEEPERS" -> getString(R.string.section_sweepers)
    "CHASM RUN" -> getString(R.string.section_chasm_run)
    "ROOFTOPS" -> getString(R.string.section_rooftops)
    "OVERPASS" -> getString(R.string.section_overpass)
    "SKYBRIDGE" -> getString(R.string.section_skybridge)
    "TRAPDOORS" -> getString(R.string.section_trapdoors)
    "TREASURY" -> getString(R.string.section_treasury)
    "TAR & FEATHER" -> getString(R.string.section_tar_feather)
    "TRAMPOLINE" -> getString(R.string.section_trampoline)
    "ROOF HOP" -> getString(R.string.section_roof_hop)
    "PENDULUMS" -> getString(R.string.section_pendulums)
    "PINCER" -> getString(R.string.section_pincer)
    "GATEKEEPER" -> getString(R.string.section_gatekeeper)
    "BLENDER" -> getString(R.string.section_blender)
    "MACHINE ROOM" -> getString(R.string.section_machine_room)
    "HELLRIDE" -> getString(R.string.section_hellride)
    "TRAIN YARD" -> getString(R.string.section_train_yard)
    "CRUSH HOUR" -> getString(R.string.section_crush_hour)
    "ROOF RUNNER" -> getString(R.string.section_roof_runner)
    "LEAPFROG" -> getString(R.string.section_leapfrog)
    "FINALE" -> getString(R.string.section_finale)
    "COIN CANYON" -> getString(R.string.section_coin_canyon)
    "GAUNTLET II" -> getString(R.string.section_gauntlet_ii)
    "MOTHERLODE" -> getString(R.string.section_motherlode)
    "BIG AIR" -> getString(R.string.section_big_air)
    "GRANDFATHER" -> getString(R.string.section_grandfather)
    "WARM-UP" -> getString(R.string.section_warm_up)
    "BREATHER" -> getString(R.string.section_breather)
    "RIDGE WEAVE" -> getString(R.string.section_ridge_weave)
    "CREST HOP" -> getString(R.string.section_crest_hop)
    "VALLEY GATES" -> getString(R.string.section_valley_gates)
    "Good Runner" -> getString(R.string.achievement_runner)
    "Lifetime Coins" -> getString(R.string.achievement_coins)
    "Unlocked Cubes" -> getString(R.string.achievement_cubes)
    "Power Collector" -> getString(R.string.achievement_powerups)
    "Mystery Seeker" -> getString(R.string.achievement_boxes)
    "Big Bubble" -> getString(R.string.achievement_bubbles)
    "Bouncer" -> getString(R.string.achievement_bounces)
    "Stay Centered" -> getString(R.string.achievement_center)
    "Homeress" -> getString(R.string.achievement_homeress)
    "Gambliphobic" -> getString(R.string.achievement_gambliphobic)
    "Cookie Clicker" -> getString(R.string.achievement_cookie)
    "Bronze" -> getString(R.string.medal_bronze)
    "Silver" -> getString(R.string.medal_silver)
    "Diamond" -> getString(R.string.medal_diamond)
    "Challenge complete" -> getString(R.string.cd_challenge_complete)
    "This upgrade does nothing." -> getString(R.string.void_line_0)
    "What did you think was going to happen?" -> getString(R.string.void_line_1)
    "Do you never learn?" -> getString(R.string.void_line_2)
    "Still nothing." -> getString(R.string.void_line_3)
    "You could have bought something useful." -> getString(R.string.void_line_4)
    "The silence is getting expensive." -> getString(R.string.void_line_5)
    "There is no refund in the dark." -> getString(R.string.void_line_6)
    "You are very persistent." -> getString(R.string.void_line_7)
    "One more will change nothing." -> getString(R.string.void_line_8)
    "Are you sure?" -> getString(R.string.void_line_9)
    "Fine." -> getString(R.string.void_line_10)
    "There was more." -> getString(R.string.void_line_11)
    "Don't look so pleased." -> getString(R.string.void_line_12)
    "The dark remembers you." -> getString(R.string.void_line_13)
    "Something follows." -> getString(R.string.void_line_14)
    "You cannot see it yet." -> getString(R.string.void_line_15)
    "Keep walking." -> getString(R.string.void_line_16)
    "Even nothing leaves a trace." -> getString(R.string.void_line_17)
    "Almost a shadow." -> getString(R.string.void_line_18)
    "Look behind you." -> getString(R.string.void_line_19)
    "A trail. For your trouble." -> getString(R.string.void_line_20)
    "You are still here." -> getString(R.string.void_line_21)
    "The silence has a shape." -> getString(R.string.void_line_22)
    "It is getting closer." -> getString(R.string.void_line_23)
    "Something wants to keep you safe." -> getString(R.string.void_line_24)
    "Or keep you here." -> getString(R.string.void_line_25)
    "A little more darkness." -> getString(R.string.void_line_26)
    "You feel it now." -> getString(R.string.void_line_27)
    "One thin veil." -> getString(R.string.void_line_28)
    "Breathe." -> getString(R.string.void_line_29)
    "The dark surrounds you." -> getString(R.string.void_line_30)
    "Nothing more. Probably." -> getString(R.string.void_echo_0)
    "The void appreciates your donation." -> getString(R.string.void_echo_1)
    "We have been here before." -> getString(R.string.void_echo_2)
    "Still listening?" -> getString(R.string.void_echo_3)
    "The silence deepens." -> getString(R.string.void_echo_4)
    else -> stableLabel
}

fun Context.achievementTitle(id: String): String = getString(when (id) {
    "runner" -> R.string.achievement_good_runner
    "coins" -> R.string.achievement_lifetime_coins
    "cubes" -> R.string.achievement_unlocked_cubes
    "powerups" -> R.string.achievement_power_collector
    "boxes" -> R.string.achievement_mystery_seeker
    "bubbles" -> R.string.achievement_big_bubble
    "bounces" -> R.string.achievement_bouncer
    "center" -> R.string.achievement_stay_centered
    "homeress" -> R.string.achievement_homeress
    "gambliphobic" -> R.string.achievement_gambliphobic
    "globetrotter" -> R.string.achievement_globetrotter
    "long_hauler" -> R.string.achievement_long_hauler
    "shardsmith" -> R.string.achievement_shardsmith
    "regular" -> R.string.achievement_regular
    "bubble_popper" -> R.string.achievement_bubble_popper
    "near_miss" -> R.string.achievement_near_miss
    "untouchable" -> R.string.achievement_untouchable
    "house_loses" -> R.string.achievement_house_loses
    "voidwalker" -> R.string.achievement_voidwalker
    "greedy" -> R.string.achievement_greedy
    "scenic_route" -> R.string.achievement_scenic_route
    "coal_miner" -> R.string.achievement_coal_miner
    "magpie" -> R.string.achievement_magpie
    "shard_hunter" -> R.string.achievement_shard_hunter
    "full_kit" -> R.string.achievement_full_kit
    "long_con" -> R.string.achievement_long_con
    "insomniac" -> R.string.achievement_insomniac
    "bankrupt" -> R.string.achievement_bankrupt
    "exactly_67" -> R.string.achievement_exactly_67
    "just_browsing" -> R.string.achievement_just_browsing
    "two_ez" -> R.string.achievement_two_ez
    "nervous_tic" -> R.string.achievement_nervous_tic
    "silent_treatment" -> R.string.achievement_silent_treatment
    "stage_fright" -> R.string.achievement_stage_fright
    "neo" -> R.string.achievement_neo
    else -> R.string.achievement_cookie_clicker
})

/** A medal family's short "what counts" line, or a challenge's goal. */
fun Context.achievementGoal(id: String): String = getString(when (id) {
    "runner" -> R.string.achievement_single_run_score
    "coins" -> R.string.achievement_collected_over_time
    "cubes" -> R.string.achievement_cube_collection
    "powerups" -> R.string.achievement_powerups_collected
    "boxes" -> R.string.achievement_boxes_opened
    "bubbles" -> R.string.achievement_hold_bubbles
    "bounces" -> R.string.achievement_wall_bounces
    "center" -> R.string.achievement_stay_middle
    "homeress" -> R.string.achievement_no_coins
    "gambliphobic" -> R.string.achievement_miss_boxes
    "globetrotter" -> R.string.achievement_goal_globetrotter
    "long_hauler" -> R.string.achievement_goal_long_hauler
    "shardsmith" -> R.string.achievement_goal_shardsmith
    "regular" -> R.string.achievement_goal_regular
    "bubble_popper" -> R.string.achievement_goal_bubble_popper
    "near_miss" -> R.string.achievement_goal_near_miss
    "untouchable" -> R.string.achievement_goal_untouchable
    "house_loses" -> R.string.achievement_goal_house_loses
    "voidwalker" -> R.string.achievement_goal_voidwalker
    "greedy" -> R.string.achievement_goal_greedy
    "scenic_route" -> R.string.achievement_goal_scenic_route
    "coal_miner" -> R.string.achievement_goal_coal_miner
    "magpie" -> R.string.achievement_goal_magpie
    "shard_hunter" -> R.string.achievement_goal_shard_hunter
    "full_kit" -> R.string.achievement_goal_full_kit
    "long_con" -> R.string.achievement_goal_long_con
    "insomniac" -> R.string.achievement_goal_insomniac
    "bankrupt" -> R.string.achievement_goal_bankrupt
    "exactly_67" -> R.string.achievement_goal_exactly_67
    "just_browsing" -> R.string.achievement_goal_just_browsing
    "two_ez" -> R.string.achievement_goal_two_ez
    "nervous_tic" -> R.string.achievement_goal_nervous_tic
    "silent_treatment" -> R.string.achievement_goal_silent_treatment
    "stage_fright" -> R.string.achievement_goal_stage_fright
    "neo" -> R.string.achievement_goal_neo
    else -> R.string.achievement_toggle_sound
})

fun Context.achievementTierName(tier: Int): String = getString(listOf(
    R.string.achievement_bronze,
    R.string.achievement_silver,
    R.string.achievement_gold,
    R.string.achievement_diamond,
)[tier.coerceIn(0, 3)])
