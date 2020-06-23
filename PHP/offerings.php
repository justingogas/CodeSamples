<?php

/*
*	File: offerings.php
*	Authors: Justin Gogas
*	Purpose: Define the get and post operations for offerings (when games are offered that can have reservations created against them).  If a get request, return offerings.  If a post, write offerings.
*/

include "databaseHeader.php";
include "environment.php";

//https://stackoverflow.com/questions/19271381/correctly-determine-if-date-string-is-a-valid-date-in-that-format
function validateDate($date, $format = "m-d-Y") {
    $d = DateTime::createFromFormat($format, $date);
    return $d && $d -> format($format) == $date;
}

function getOfferingsStatement() {

	return "
		select
			offerings.id,
			offerings.game_id as gameId,
			offerings.offering,
			offerings.created,

			locations.time_zone as timeZone,

			games.location_id as locationId,
			games.name,
			games.display_name as displayName,
			games.price,
			games.description,
			games.participants as maximumParticipants,
			case
				when bookings.participants is null then games.participants
				else (games.participants - bookings.participants)
			end as emptySlots

		from offerings

		inner join games
		on games.id = offerings.game_id

		inner join locations
		on games.location_id = locations.id

		left join (

			select sum(participants) as participants, offering_id
			from bookings
			group by offering_id

		) bookings
		on bookings.offering_id = offerings.id

		where 1 = 1
		and games.active = 1
		and games.location_id = :locationId
	";
}


// Retrieve the list of bookings if this is a get request.
if ($_SERVER["REQUEST_METHOD"] == "GET") {

	// Get only the list of reservations.
	if (isSet($_GET["action"]) && $_GET["action"] == "getOfferings") {

		$sql = getOfferingsStatement();

		// If a specific offering was named, then add it into the clause.
		if (isSet($_GET["offeringId"])) {

			$offeringId = htmlSpecialChars($_GET["offeringId"]);

			$sql .= "
				and id = :offeringId
			";
		}

		// If display full is zero, then do not display full games.
		if (isSet($_GET["displayFull"])) {

			$displayFull = htmlSpecialChars($_GET["displayFull"]);

			if ($displayFull == "0") {

				$sql .= "
					and (games.participants - bookings.participants) > 0
				";
			}
		}

		// By default, only return future offerings.  
		if (!isSet($_GET["type"])) {

			$sql .= "
				and offering >= now()
			";

		} else {

			$type = htmlSpecialChars($_GET["type"]);

			if ($type == "expired") {

				$sql .= "
					and offering < now()
				";
			}

			// The default case is to return future offerings, so any other value will leave the where clause off, returning all results.  This doesn't need to match to "all", so it is symbolic.
			else if ($type == "all") {}
		}

		$sql .= "
			order by offering asc
		";

		$parameters = array(
			"locationId" => $_ENV["locationId"]
		);

		if (isSet($_GET["reservationId"])) {
			$parameters["reservationId"] = htmlSpecialChars($_GET["reservationId"]);
		}

		if (isSet($_GET["offeringId"])) {
			$parameters["offeringId"] = htmlSpecialChars($_GET["offeringId"]);
		}

		$values = executeQuery($link, $sql, $parameters);

		if (!$values) {
			http_response_code(400);
			exit;
		}

		// Return JSON of the values.
		header("Content-type: application/json");

		echo json_encode($values);
	}


	// Get the schedule of all open slots with reservation data mixed.
	else if (isSet($_GET["action"]) && $_GET["action"] == "getSchedule") {

		// Retrieve all reservations.
		$sql = getOfferingsStatement();

		if (isSet($_GET["gameDate"]) && validateDate($_GET["gameDate"], "Y-m-d")) {
			$today = date($_GET["gameDate"]);
			$tomorrow = date("Y-m-d", strtotime($today . " + 1 days"));
		} else {
			$today = date("Y-m-d");
			$tomorrow = date("Y-m-d", strtotime($today . " + 1 days"));
		}

		// If a specific setting was named, then return that setting.
		if (isSet($_GET["gameId"])) { $sql .= " and games.id = :gameId "; }

		// Add the date filter in.
		$sql .= " and offerings.offering >= :today and offerings.offering <= :tomorrow ";

		$sql .= "
			order by offering asc
		";

		// Bind in today and tomorrow to limit the schedule to only a single day display.
		$parameters = array(
			"locationId" => $_ENV["locationId"],
			"today" => $today,
			"tomorrow" => $tomorrow
		);

		if (isSet($_GET["gameId"])) { 
			$parameters["gameId"] = strval(htmlSpecialChars($_GET["gameId"]));
		}

		$values = executeQuery($link, $sql, $parameters);

		// Execute the statement and return a 400 if it fails.
		if (!$values) {
			http_response_code(400);
			exit;
		}

		// Return JSON of the values.
		header("Content-type: application/json");

		echo json_encode($values);
	}


	// Create the offerings 60 days (by default) out.
	// Move this to a private folder eventually.
	else if (isSet($_GET["action"]) && $_GET["action"] == "createOfferings") {

		// Default the offerings creation to be 60 days from now.  Overwrite this if passed in.
		$days = 60;

		if (isSet($_GET["days"]) && !is_numeric($_GET["days"])) {
			$days = htmlSpecialChars($_GET["days"]);
		}

		// Retrieve the current offerings that have not passed to find out which ones do not need to be created.
		$sql = getOfferingsStatement() . " and offering > now() ";

		$preparedStatement = $link -> prepare($sql);

		$parameters = array(
			"locationId" => $_ENV["locationId"]
		);

		$currentOfferings = executeQuery($link, $sql, $parameters);

		if (!$currentOfferings) {
			http_response_code(400);
			exit;
		}

		else {

			// Retrieve all game definitions.
			$sql = "
				select
					games.id as gameId,
					games.name,
					games.display_name as displayName,
					games.description,
					games.participants as gameParticipants,
					games.length,
					games.before_length as beforeLength,
					games.after_length as afterLength,
					games.start_time as startTime,
					games.end_time as endTime,
					games.price,
					games.participants

				from games

				inner join locations
				on locations.id = :locationId

				where 
					games.active = 1
					and games.complete = 0
			";

			if (isSet($_GET["gameId"])) { $sql .= " and games.id = :gameId "; }

			$parameters = array(
				"locationId" => $_ENV["locationId"]
			);

			if (isSet($_GET["gameId"])) { $parameters["gameId"] = strval(htmlSpecialChars($_GET["gameId"])); }

			$gamesValues = executeQuery($link, $sql, $parameters);

			// Execute the statement and return a 400 if it fails.
			if (!$gamesValues) {
				http_response_code(400);
				exit;
			}

			$offeringSchedule = [];

			$today = date("Y-m-d");

			for ($day = 0; $day <= $days; $day++) {

				// Iterate through all the games and expand the offering times out.
				foreach ($gamesValues as $game) {

					$currentDay = date("Y-m-d", strtotime($today . " + " . $day . " days"));
				
					$startInterface = new DateTime($today);
					$gameStartTime = explode(":", $game["startTime"]);		// 11:00:00
					date_time_set($startInterface, $gameStartTime[0], $gameStartTime[1], $gameStartTime[2]);

					$endInterface = new DateTime($today);
					$gameEndTime = explode(":", $game["endTime"]);			// 23:00:00
					date_time_set($endInterface, $gameEndTime[0], $gameEndTime[1], $gameEndTime[2]);

					$interval = new DateInterval("PT" . ($game["length"] + $game["beforeLength"] + $game["afterLength"]) . "M");	// 60 + 30 + 30  = 120 minutes
					$period = new DatePeriod($startInterface, $interval, $endInterface);

					// A period is created that increments every interval minutes (120 in this case) so that individual offerings are created.
					foreach ($period as $timeCounter) {

						$offering = $game;

						$offering["startTime"] = $timeCounter -> format("H:i:s");		// End time is not relevant to the offering since it can be derived from the start time + game length.
						$offering["emptySlots"] = $game["participants"];
						$offering["inputDate"] = $currentDay;

						// Iterate through all the current offerings and do not add them if they exist.
						$addOffering = true;

						foreach ($currentOfferings as $currentOffering) {

							if ($offering["gameId"] == $currentOffering["gameId"] && ($offering["inputDate"] . " " . $offering["startTime"]) == $currentOffering["offering"]) {
								$addOffering = false;
								break;
							}
						}

						if ($addOffering) {
							array_push($offeringSchedule, $offering);
						}
					}
				}
			}

			foreach ($offeringSchedule as $offering) {

				// The offering schedule now contains all of the offerings that need to be created.
				$offeringDateTime = $offering["inputDate"] . " " . $offering["startTime"];
				
				$sql = "
					insert into offerings (
						game_id,
						offering,
						created
					)

					values (
						:gameId,
						:offeringDateTime,
						now()
					)
				";

				$preparedStatement = $link -> prepare($sql);
				$preparedStatement -> bindParam(":gameId", $offering["gameId"]);
				$preparedStatement -> bindParam(":offeringDateTime", $offeringDateTime);
				$preparedStatement -> execute();
			}
		}

		// Return JSON of the values.
		header("Content-type: application/json");

		echo json_encode($offeringSchedule);

		$preparedStatement = null;
	}

	else {
		http_response_code(400);
		exit;
	}
}

include "databaseFooter.php";

?>