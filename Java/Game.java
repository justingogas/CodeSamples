package server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Game {

	protected ArrayList<HashMap> controls;
	protected String displayName;
	protected Timer gameTimer;
	protected int id;
	protected ArrayList<HashMap> interfaces;
	protected long length;
	protected String name;
	protected ArrayList<GameObjectiveGroup> objectiveGroups;
	protected long secondsLeft;
	protected String state;			// Stopped, started, idle, ready, paused.
	protected ArrayList<HashMap> timeControls;
	protected String winState;		// victory, defeat, or blank.

	// Initialize the game according to the settings that it needs to load for each game.
	public Game(int inputId, String inputName) {

		boolean initialized = false;

		System.out.println("Initializing " + inputName + ".");

		JSONParser jsonParser = new JSONParser();
		JSONObject gameDefinition = new JSONObject();

		// Get the game properties according to the name of the game ("<gameName>.json");
		try {
			Object parsedObject = jsonParser.parse(new FileReader("./server/" + inputName + ".json"));
			gameDefinition = (JSONObject) parsedObject;
		}

		catch (FileNotFoundException e) { e.printStackTrace(); System.out.println("Game not created."); }
		catch (IOException e) { e.printStackTrace(); System.out.println("Game not created."); }
		catch (ParseException e) { e.printStackTrace(); System.out.println("Game not created."); }

		// Set the parameters of the game based on the input properties and values from the JSON settings for the game.
		displayName = (String)gameDefinition.get("displayName");
		id = inputId;
		length = (long)gameDefinition.get("length");
		name = (String)gameDefinition.get("name");
		secondsLeft = (long)gameDefinition.get("length");
		state = "stopped";
		winState = "";

		// Set up the interfaces to various components if any are defined.
		interfaces = new ArrayList<HashMap>();
		interfaces = createInterfaces((JSONArray)gameDefinition.get("interfaces"));
		initializeInterfaces();
		System.out.println(interfaces.size() + " interfaces defined.");

		// Set up the controls to various components if any are defined.  Interfaces must be created before this happens.
		controls = createControls((JSONArray)gameDefinition.get("controls"));
		System.out.println(controls.size() + " controls defined.");

		// Create the objective groups based on the game's objective groups.
		objectiveGroups = createObjectiveGroups((JSONArray)gameDefinition.get("objectiveGroups"));
		System.out.println(objectiveGroups.size() + " objective groups created.");

		// Create an array list to hold timer controls and fill it with any initial time controls.
		timeControls = new ArrayList<HashMap>();
		processInitialTimeControls((ArrayList<HashMap>)gameDefinition.get("timeControls"), secondsLeft);
		System.out.println(timeControls.size() + " time controls queued.");

		System.out.println(name + " created.");
	}


	////////////////////////////////////////////////////////////////////////////
	// Begin time control methods. /////////////////////////////////////////////

		// Create the timer that keeps track of the game's progress.  The run() function is called each second tick.
		public class SecondTimer extends TimerTask {

			public void run() {

				if (state == "started") {

					System.out.println("timer called, time = " + secondsLeft);

					secondsLeft--;

					// Look at the top element in the timer queue to see if there is an event to run.
					if (timeControls.size() > 0) {

						// If any timer events are found, then see if they can be executed.
						for (HashMap timeControl : timeControls) {

							if (
								Math.toIntExact((long)timeControl.get("time")) >= Math.toIntExact(secondsLeft)
								&&
								!(boolean)timeControl.get("executed")
							) {

								// Execute the control.
								runControl(
									//Math.toIntExact((int)timeControl.get("controlId")),
									(String)timeControl.get("controlName"),
									(String)timeControl.get("controlValue")
								);

								// Mark the control as executed for clearing out later.
								timeControl.put("executed", true);
							}
						}


						// Clear out any executed time controls by iterating until one run through the time controls list does not find any executed controls.  Any executed control is removed.
						boolean removedAllExecuted = false;

						while (!removedAllExecuted) {

							removedAllExecuted = true;

							for (int i = 0; i < timeControls.size(); i++) {

								if ((boolean)timeControls.get(i).get("executed") == true) {

									removedAllExecuted = false;
									timeControls.remove(i);
									break;
								}
							}
						}
					}

					// Game completes when the timer == 0 and run the failure control sequence.
					if (secondsLeft <= 0) {

						complete();
						this.cancel();

						// Run the failure control sequence for this objective group if the game ended in failure.
						// Failure control sequence items must have a delay of 0 because the timer is no longer running after this point.  Any delays need to be built into the target interface's actions.
						if (winState == "defeat") {

							for (GameObjectiveGroup objectiveGroup: objectiveGroups) {

								if (!objectiveGroup.isComplete()) {
									queueTimeControl((ArrayList<HashMap>)objectiveGroup.getControlSequence().get("fail"), secondsLeft);
									break;
								}
							}
						}
					}
				}
			}
		}


		// Add seconds to the countdown timer in the game.  The game thread is responsible for reaching out to the proper interface to tell it to add time.
		public boolean addSeconds(long inputSeconds) {
			secondsLeft += inputSeconds;
			return true;
		}


		// Pause the game's countdown timer.
		public boolean pause() {

			if (winState == "" && state == "started") {
				state = "paused";
				gameTimer.cancel();
			}

			return (state == "paused");
		}


		// Resume the game's countdown timer from a stopped state.
		public boolean resume() {

			if (winState == "" && (state == "stopped" || state == "paused")) {
				state = "started";
				gameTimer = new Timer();
				gameTimer.schedule(new SecondTimer(), 0, 1000);
				return true;
			}

			return (state == "started");
		}


		// Start the game's countdown timer.
		public boolean start() {

			if (winState == "" && state == "stopped") {
				state = "started";
				gameTimer = new Timer();
				gameTimer.schedule(new SecondTimer(), 0, 1000);
			}

			return (state == "started");
		}

	// End time control methods. ///////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////



	////////////////////////////////////////////////////////////////////////////
	// Begin generic getter control methods. ///////////////////////////////////

		public ArrayList getControls() { return controls; }
		public String getDisplayName() { return displayName; }
		public int getId() { return id; }
		public ArrayList<HashMap> getInterfaces() { return interfaces; }
		public long getLength() { return length; }
		public String getName() { return name; }
		public ArrayList<GameObjectiveGroup> getObjectiveGroups() { return objectiveGroups; }
		public long getSecondsLeft() { return secondsLeft; }
		public String getState() { return state; }
		public String getWinState() { return winState; }

	// End generic getter control methods. /////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////



	////////////////////////////////////////////////////////////////////////////
	// Begin game control methods. /////////////////////////////////////////////

		// Take action to complete the game, which forces its winState into either a victory or defeat depending on the objectives completion status.
		public boolean complete() {

			state = "stopped";

			if (isComplete()) {
				winState = "victory";
			} else {
				winState = "defeat";
			}

			return true;
		}


		// Find out if this game is complete, which is determined if all objective groups are complete.  This implies all objectives are complete as well, but if overridden, objectives might not all be complete.
		public boolean isComplete() {

			boolean allObjectiveGroupsCompleted = true;

			// Iterate through all objectives trying to find one that is not complete.
			for (GameObjectiveGroup objectiveGroup : objectiveGroups) {

				if (!objectiveGroup.isComplete()) {
					allObjectiveGroupsCompleted = false;
					break;
				}
			}

			return (winState == "" && allObjectiveGroupsCompleted);
		}

	// End game control methods. ////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////



	/////////////////////////////////////////////////////////////////////////////
	// Begin objective group and objective methods. /////////////////////////////

		public boolean completeObjective(String inputObjectiveName) {

			boolean returnObjectiveCompleted = false;

			// Find the specified objective by name in all objective groups.
			for (GameObjectiveGroup objectiveGroup : objectiveGroups) {

				// The delay of control sequences is cumulative from the last control in sequence, so get the current game timer as a point of reference for the sequence of actions and their delays.
				long runningTime = secondsLeft;

				// If the objective is completed, then run its control sequence.
				if (objectiveGroup.completeObjective(inputObjectiveName, secondsLeft)) {

					HashMap objectiveControlSequence = objectiveGroup.getObjectiveControlSequence(inputObjectiveName);

					// First, queue the objective's control sequence.  The adjusted running time is returned after the control delays are calculated so that group control sequences can be run after.
					if (objectiveControlSequence != null) {
						runningTime = queueTimeControl((ArrayList<HashMap>)objectiveControlSequence.get("complete"), runningTime);
					}

					// Second, if this objective's completion causes the group to be complete, execute the group's success sequence.
					if (objectiveGroup.isComplete()) {
						runningTime = queueTimeControl((ArrayList<HashMap>)objectiveGroup.getControlSequence().get("complete"), runningTime);
					}

					// Third, if there are any time controls that are no longer needed now that this objective group was completed, then get rid of them.
					deleteTimeControlByObjectiveCompletion(inputObjectiveName, "objective");

					returnObjectiveCompleted = true;

					break;
				}
			}

			return returnObjectiveCompleted;
		}


		// Bypass the objectives and complete the entire group.
		public boolean completeObjectiveGroup(String inputObjectiveGroupName) {

			boolean objectiveGroupCompleted = false;

			if (objectiveGroups != null) {

				for (GameObjectiveGroup objectiveGroup : objectiveGroups) {

					if (objectiveGroup.getName().equalsIgnoreCase(inputObjectiveGroupName)) {

						objectiveGroup.complete(secondsLeft);

						// Get the control sequence for the group and run it.
						queueTimeControl((ArrayList<HashMap>)objectiveGroup.getControlSequence().get("complete"), secondsLeft);
						objectiveGroupCompleted = true;

						// If there are any time controls that are no longer needed now that this objective group was completed, then get rid of them.
						deleteTimeControlByObjectiveCompletion(inputObjectiveGroupName, "objectiveGroup");

						break;
					}
				}
			}

			return objectiveGroupCompleted;
		}


		// Iterate over all time controls and delete any that are deleteable if an objective name and type (objective, objectiveGroup) are found.
		public void deleteTimeControlByObjectiveCompletion(String inputObjectiveName, String inputType) {

			if (timeControls.size() > 0) {

				boolean removedAllUnneededControls = false;

				while (!removedAllUnneededControls) {

					removedAllUnneededControls = true;

					for (int i = 0; i < timeControls.size(); i++) {

						for (HashMap objective : (ArrayList<HashMap>)timeControls.get(i).get("deleteIfObjectiveCompletes")) {

							if (
								inputType.equalsIgnoreCase((String)objective.get("type"))
								&&
								inputObjectiveName.equalsIgnoreCase((String)objective.get("name"))
							) {
								removedAllUnneededControls = false;
								timeControls.remove(i);
								break;
							}
						}
					}
				}
			}
		}


		// Create objective groups from the game definitions.
		public ArrayList<GameObjectiveGroup> createObjectiveGroups(JSONArray inputObjectiveGroups) {

			ArrayList<GameObjectiveGroup> newObjectiveGroups = new ArrayList<GameObjectiveGroup>();

			int objectiveGroupIdCounter = 0;
			int objectiveIdCounter = 0;

			// The objective group has two values, a group name and an array of objectives.  This structure will be flattened as the group is only used for display purposes.
			Iterator<JSONObject> objectiveGroupIterator = inputObjectiveGroups.iterator();

			// Iterate on all objective groups to extract the objectives.
			while(objectiveGroupIterator.hasNext()) {

				JSONObject objectiveGroup = objectiveGroupIterator.next();

				if ((boolean)objectiveGroup.get("enabled")) {

					JSONArray objectiveArray = (JSONArray)objectiveGroup.get("objectives");
					Iterator<JSONObject> objectiveIterator = objectiveArray.iterator();

					// Create the objective group which also creates the objectives from the JSON.
					newObjectiveGroups.add(
						new GameObjectiveGroup(
							(HashMap)objectiveGroup.get("controlSequence"),
							(String)objectiveGroup.get("description"),
							(String)objectiveGroup.get("displayName"),
							objectiveIdCounter,
							Math.toIntExact((long)objectiveGroup.get("index")),
							(String)objectiveGroup.get("name"),
							(JSONArray)objectiveGroup.get("objectives")
						)
					);
				}
			}

			return newObjectiveGroups;
		}


		// Get the objectiveGroups as a JSON array object.
		public JSONArray getObjectiveGroupsJson() {

			JSONArray objectiveGroupsArray = new JSONArray();

			for (GameObjectiveGroup objectiveGroup : objectiveGroups) {
				objectiveGroupsArray.add(objectiveGroup.toJson());
			}

			return objectiveGroupsArray;
		}


		// Process the initial time controls into the time control list.
		private void processInitialTimeControls(ArrayList<HashMap> inputTimeControls, long inputRunningTime) {

			if (inputTimeControls != null) {
				for (HashMap timeControl : inputTimeControls) {
					queueTimeControl((ArrayList<HashMap>)timeControl.get("controlSequence"), inputRunningTime);					
				}
			}
		}


		// Add a control to the list of time controls to be executed after a certain delay.  If there are no delays for a control, then execute it immediately.
		private long queueTimeControl(ArrayList<HashMap> inputControlSequence, long inputRunningTime) {

			long runningTime = inputRunningTime;

			if (inputControlSequence != null) {

				for (HashMap controlSequence : inputControlSequence) {

					if ((boolean)controlSequence.get("enabled")) {

						int controlId = getControlIdByControlName((String)controlSequence.get("name"));

						if (controlId != -1) {

							long controlTimeDelay = (long)controlSequence.get("delay");
							ArrayList<HashMap> deleteIfObjectiveCompletes = (ArrayList<HashMap>)controlSequence.get("deleteIfObjectiveCompletes");
							String controlValue = (String)controlSequence.get("value");
							String controlName = (String)controlSequence.get("controlName");
							String name = (String)controlSequence.get("name");

							// Run immediately if the delay is 0.
							if (controlTimeDelay == 0) {
								runControl(controlName, controlValue);
							}

							else {

								runningTime -= controlTimeDelay;

								HashMap newControl = new HashMap();
								newControl.put("controlId", controlId);
								newControl.put("controlValue", controlValue);
								newControl.put("controlName", controlName);
								newControl.put("deleteIfObjectiveCompletes", deleteIfObjectiveCompletes);
								newControl.put("executed", false);
								newControl.put("name", name);
								newControl.put("time", runningTime);

								timeControls.add(newControl);
							}
						}
					}
				}
			}

			return runningTime;
		}


		// Reset an objective to its incomplete state.
		//public boolean resetObjective(int inputObjectiveGroupId, int inputObjectiveId) {
		public boolean resetObjective(String inputObjectiveName) {

			boolean returnObjectiveReset = false;

			if (objectiveGroups != null) {

				for (GameObjectiveGroup objectiveGroup : objectiveGroups) {

					// If the objective is in the objective group, then reset both the objective group and objective.
					//if (objectiveGroup.getId() == inputObjectiveGroupId) {
					if (objectiveGroup.containsObjective(inputObjectiveName)) {

						returnObjectiveReset = (
							objectiveGroup.resetObjective(inputObjectiveName)
							&&
							objectiveGroup.reset()
							&&
							(boolean)sendToInterface(
								//(String)currentInterface.get("location"),
								getInterfaceLocationByInterfaceName(objectiveGroup.getObjectiveInterfaceName(inputObjectiveName)),
								"resetObjective",
								inputObjectiveName,
								""
							).get("result")
						);
						break;
					}
				}
			}

			return returnObjectiveReset;
		}


		// Reset the objective group (does not affect the individual objectives).
		//public boolean resetObjectiveGroup(int inputObjectiveGroupId) {
		public boolean resetObjectiveGroup(String inputObjectiveGroupName) {

			boolean objectiveGroupReset = false;

			for (GameObjectiveGroup objectiveGroup : objectiveGroups) {

				//if (objectiveGroup.getId() == inputObjectiveGroupId) {
				if (objectiveGroup.getName().equalsIgnoreCase(inputObjectiveGroupName)) {

					objectiveGroupReset = objectiveGroup.reset();
					break;
				}
			}

			return objectiveGroupReset;
		}

	// End objective group and objective methods. //////////////////////////////
	////////////////////////////////////////////////////////////////////////////



	////////////////////////////////////////////////////////////////////////////
	// Begin conrols methods. //////////////////////////////////////////////////

		// Create the controls as hash maps in an array list.
		public ArrayList<HashMap> createControls(JSONArray controlList) {

			ArrayList<HashMap> newControls = new ArrayList<HashMap>();

			Iterator<JSONObject> iterator = controlList.iterator();
			while (iterator.hasNext()) {

				JSONObject controlInList = iterator.next();		// Interface is a reserved word.
				HashMap controlItem = new HashMap();

				if ((boolean)controlInList.get("enabled")) {

					// Get the interface and check its enabled flag to tell if the interface is active, and thus if the control is active.
System.out.println((String)controlInList.get("interface"));
					HashMap controlInterface = getInterfaceByInterfaceName((String)controlInList.get("interface"));
System.out.println(controlInterface);
					// Add an active field for its connection status.  Set to true once the control is initialized.	
					if (controlInterface != null && (boolean)controlInterface.get("active")) {
						controlItem.put("active", true);
					} else {
						controlItem.put("active", false);
					}

					controlItem.put("description", controlInList.get("description"));
					controlItem.put("displayName", controlInList.get("displayName"));
					controlItem.put("id", controlInList.get("id"));
					controlItem.put("index", controlInList.get("index"));
					controlItem.put("interface", controlInList.get("interface"));
					controlItem.put("name", controlInList.get("name"));

					// States are a JSON array of JSON objects that need to be turned into an array list of hash maps.
					ArrayList<HashMap> newStates = new ArrayList<HashMap>();
					JSONArray statesArray = (JSONArray)controlInList.get("states");
					Iterator<JSONObject> stateIterator = statesArray.iterator();

					while (stateIterator.hasNext()) {

						JSONObject stateInList = stateIterator.next();
						HashMap stateItem = new HashMap();
						stateItem.put("description", stateInList.get("description"));
						stateItem.put("name", stateInList.get("name"));
						stateItem.put("value", stateInList.get("value"));
						newStates.add(stateItem);
					}

					controlItem.put("states", newStates);
					controlItem.put("type", controlInList.get("type"));
					newControls.add(controlItem);
				}
			}

			return newControls;
		}


		// Retrieve the arraylist of interfaces hash maps as a JSON array.
		public JSONArray getControlsJson() {

			JSONArray controlsList = new JSONArray();

			for (HashMap control : controls) {

				JSONObject controlItem = new JSONObject();
				controlItem.put("active", control.get("active"));
				controlItem.put("description", control.get("description"));
				controlItem.put("displayName", control.get("displayName"));
				controlItem.put("id", control.get("id"));
				controlItem.put("index", control.get("index"));
				controlItem.put("interface", control.get("interface"));
				controlItem.put("name", control.get("name"));

				ArrayList states = new ArrayList<HashMap>();
				states = (ArrayList)control.get("states");
				JSONArray statesJson = new JSONArray();

				for (int j = 0; j < states.size(); j++) {

					JSONObject stateItem = new JSONObject();
					stateItem.put("description", ((HashMap)states.get(j)).get("description"));
					stateItem.put("name", ((HashMap)states.get(j)).get("name"));
					stateItem.put("value", ((HashMap)states.get(j)).get("value"));
					statesJson.add(stateItem);
				}

				controlItem.put("states", statesJson);
				controlItem.put("type", control.get("type"));
				controlsList.add(controlItem);
			}

			return controlsList;
		}


		// Retrieve a control ID from a control name.
		public int getControlIdByControlName(String controlName) {

			int returnControlId = -1;

			for (HashMap control : controls) {

				if (((String)control.get("name")).equalsIgnoreCase(controlName)) {

					returnControlId = Math.toIntExact((long)control.get("id"));
					break;
				}
			}

			return returnControlId;
		}


		// Not sure if needed?
		// Find any inactive controls and initialize them.
		public void initializeInactiveControls(String serverLocation) {

			/*for (HashMap control : controls) {

				int controlId = Math.toIntExact((long)control.get("id"));

				if (
					!(boolean)control.get("active")
					&&
					(boolean)(runControl(controlId, "initialize " + controlId + " " + serverLocation).get("result"))
				) {
					control.put("active", true);
				}
			}*/
		}


		// Run a control by getting its interface and sending the value for the control to the device in an HTTP request.
		//public JSONObject runControl(int inputControlId, String inputControlValue) {
		public JSONObject runControl(String inputControlName, String inputControlValue) {
	System.out.println("in runControl: controlName = " + inputControlName + ", controlValue = " + inputControlValue);
			JSONObject message = new JSONObject();

			String interfaceLocation = getInterfaceLocationByControlName(inputControlName);
			//String parameters = "command=control&controlId=" + controlId + "&value=\"" + requestTokens[3] + "\"";
			String parameters = "command=control&name=" + inputControlName + "&value=" + inputControlValue;

			try {

				// Create the connection to send the hint to.
				URL url = new URL("http://" + interfaceLocation);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setConnectTimeout(5000);
	System.out.println("sending control to http://" + interfaceLocation);
				// Add the hint to the parameters.
				DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
				outputStream.writeBytes(parameters);
				outputStream.flush();
				outputStream.close();

				// Get the response from the interface.
				int responseCode = connection.getResponseCode();
				System.out.println("\nSending 'POST' request to URL : " + url.toString());
				System.out.println("Post parameters : " + parameters);
				System.out.println("Response Code : " + responseCode);

				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String inputLine = "";
				StringBuffer interfaceResponse = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					interfaceResponse.append(inputLine);
				}
				in.close();

				message.put("message", "Control sent.  Response: " + interfaceResponse.toString());
				message.put("result",  true);
			}

			catch (java.net.SocketTimeoutException e) {
				message.put("message", "Error sending control: timeout to interface.");
				message.put("result",  false);
			}

			catch (java.io.IOException e) {
				message.put("message", "Error sending control: IO error.");
				message.put("result",  false);
			}

			return message;
		}

	// End conrols methods. ////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////



	////////////////////////////////////////////////////////////////////////////
	// Begin interface methods. ////////////////////////////////////////////////

		// Create the interfaces as hash maps in an array list.
		public ArrayList<HashMap> createInterfaces(JSONArray interfaceList) {

			ArrayList<HashMap> newInterfaces = new ArrayList<HashMap>();

			Iterator<JSONObject> interfaceListIterator = interfaceList.iterator();
			while (interfaceListIterator.hasNext()) {

				JSONObject currentInterface = interfaceListIterator.next();		// Interface is a reserved word.

				if ((boolean)currentInterface.get("enabled")) {

					HashMap newInterface = new HashMap();

					newInterface.put("active", false);		// Set to true once the control is initialized.

					// Iterate over all keys in the interface and add them with their values.
					for (Iterator interfaceKeyIterator = currentInterface.keySet().iterator(); interfaceKeyIterator.hasNext();) {
						String key = (String)interfaceKeyIterator.next();
						newInterface.put(key, currentInterface.get(key));
					}

					newInterfaces.add(newInterface);
				}
			}

			return newInterfaces;
		}


		// Retrieve the arraylist of interface hash maps as a JSON array.
		public JSONArray getInterfacesJson() {

			JSONArray interfaceList = new JSONArray();

			for (HashMap currentInterface : interfaces) {

				JSONObject newInterface = new JSONObject();

				// Iterate over all keys in the interface and add them with their values.
				for (Iterator iterator = currentInterface.keySet().iterator(); iterator.hasNext();) {
					String key = (String)iterator.next();
					newInterface.put(key, currentInterface.get(key));
				}

				interfaceList.add(newInterface);
			}

			return interfaceList;
		}


		// Retrieve the interface location by interface name.
		public String getInterfaceLocationByInterfaceName(String interfaceName) {

			String returnInterfaceLocation = "";

			// Iterate through the interfaces to find the matching name.
			for (HashMap currentInterface : interfaces) {

				if (((String)currentInterface.get("name")).equalsIgnoreCase(interfaceName)) {

					returnInterfaceLocation = (String)currentInterface.get("location");
					break;
				}
			}

			return returnInterfaceLocation;
		}


		// Retrieve the interface by interface name.
		public HashMap getInterfaceByInterfaceName(String interfaceName) {

			HashMap returnInterface = null;

			// Iterate through the interfaces to find the matching name.
			for (HashMap currentInterface : interfaces) {

				if (((String)currentInterface.get("name")).equalsIgnoreCase(interfaceName)) {

					returnInterface = currentInterface;
					break;
				}
			}

			return returnInterface;
		}


		// Retrieve the interface location by interface ID.
		public String getInterfaceLocationByInterfaceId(int interfaceId) {

			String returnInterfaceLocation = "";

			// Iterate through the interfaces to find the matching name.
			for (HashMap currentInterface : interfaces) {

				if ((Math.toIntExact((long)currentInterface.get("id"))) == interfaceId) {

					returnInterfaceLocation = (String)currentInterface.get("location");
					break;
				}
			}

			return returnInterfaceLocation;
		}


		// Retrieve the interface location by control ID, which looks up the interface name for the control and gets the corresponding interface location.
		public String getInterfaceLocationByControlId(int controlId) {

			String returnInterfaceLocation = "";
			String interfaceName = "";

			// Iterate through the interfaces to find the matching name.
			for (HashMap control : controls) {

				if ((Math.toIntExact((long)control.get("id"))) == controlId) {

					interfaceName = (String)control.get("interface");
					break;
				}
			}

			return getInterfaceLocationByInterfaceName(interfaceName);
		}


		public String getInterfaceLocationByControlName(String inputControlName) {

			String returnInterfaceLocation = "";
			String interfaceName = "";

			// Iterate through the interfaces to find the matching name.
			for (HashMap control : controls) {

				//if ((Math.toIntExact((long)control.get("id"))) == controlId) {
				if (((String)control.get("name")).equalsIgnoreCase(inputControlName)) {

					interfaceName = (String)control.get("interface");
					break;
				}
			}

			return getInterfaceLocationByInterfaceName(interfaceName);
		}


		// Retrieve the interface configuraton by interface name.
		public JSONArray getInterfaceConfigurationByInterfaceName(String interfaceName) {

			JSONArray returnInterfaceConfiguration = new JSONArray();

			// Iterate through the interfaces to find the matching name.
			for (HashMap currentInterface : interfaces) {

				if (((String)currentInterface.get("name")).equalsIgnoreCase(interfaceName)) {

					returnInterfaceConfiguration = (JSONArray)currentInterface.get("configuration");
					break;
				}
			}

			return returnInterfaceConfiguration;
		}


		// Initialize all interfaces and set their active flag.  Initalization is in the form of three commands:
		//	1. "configuration server <server IP address>:<server port>": this will initialize the control with the server it should be contacting for communication.
		//	2. "configuration gameId <game ID>": this will tell the interface what other interface to report to when controls get completed or failed.  This will almost always be a central game server.
		//	3. "initialize": this will instruct the interface to initialize all controls back to their starting values.
		public void initializeInterfaces() {

			for (HashMap currentInterface : interfaces) {

				boolean interfaceInitialized = false;

				// Initialize the interface's reporting server.
				if ((boolean)currentInterface.get("initializable")) {

					interfaceInitialized = (boolean)(
							sendToInterface(
								(String)currentInterface.get("location"),
								"configuration",
								"server",
								getInterfaceLocationByInterfaceName((String)currentInterface.get("returnInterface"))
							).get("result")
						);

					// Initialize the interface's game ID.
					interfaceInitialized = (boolean)(
							sendToInterface(
								(String)currentInterface.get("location"),
								"configuration",
								"gameId",
								Integer.toString(getId())
							).get("result")
						);

					// Initialize the interface's control values.
					interfaceInitialized =  (boolean)(
							sendToInterface(
								(String)currentInterface.get("location"),
								"initialize",
								"",
								""
							).get("result")
						);
				}

				// If an interface is not initializable, assume it is enabled and reachable.
				else {
					interfaceInitialized = true;
				}

				if (interfaceInitialized) {
					currentInterface.put("active", true);
				} else {
					currentInterface.put("active", false);
				}
			}
		}


		// Send a command to an interface by getting its interface and sending the value for the control to the device in an HTTP request.
		public JSONObject sendToInterface(String inputInterfaceLocation, String inputCommand, String inputName, String inputValue) {

			JSONObject message = new JSONObject();

			//String interfaceLocation = getInterfaceLocationByControlId(controlId);
			//String parameters = "command=control&controlId=" + controlId + "&value=\"" + requestTokens[3] + "\"";
			String parameters = "command=" + inputCommand;

			// Add additional parameters if they  exist.
			if (inputName != "") { parameters += "&name=" + inputName; }
			if (inputValue != "") { parameters += "&value=" + inputValue; }

			try {

				// Create the connection to send the hint to.
				URL url = new URL("http://" + inputInterfaceLocation);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setConnectTimeout(5000);
System.out.println("sending to interface URL http://" + inputInterfaceLocation);
				// Add the hint to the parameters.
				DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
				outputStream.writeBytes(parameters);
				outputStream.flush();
				outputStream.close();

				// Get the response from the interface.
				int responseCode = connection.getResponseCode();
				System.out.println("\nSending 'POST' request to URL : " + url.toString());
				System.out.println("Post parameters : " + parameters);
				System.out.println("Response Code : " + responseCode);

				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String inputLine = "";
				StringBuffer interfaceResponse = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					interfaceResponse.append(inputLine);
				}
				in.close();

				message.put("message", "Control sent.  Response: " + interfaceResponse.toString());
				message.put("result",  true);
			}

			catch (java.net.SocketTimeoutException e) {
				message.put("message", "Error sending control: timeout to interface.");
				message.put("result",  false);
			}

			catch (java.io.IOException e) {
				message.put("message", "Error sending control: IO error.");
				message.put("result",  false);
			}

			return message;
		}

	// End interface methods. //////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////



	////////////////////////////////////////////////////////////////////////////
	// Begin representation methods. ///////////////////////////////////////////

		// Print out this object as a string representation.
		public JSONObject toJson() {

			initializeInactiveControls(getInterfaceLocationByInterfaceName("server"));

			JSONObject game = new JSONObject();

			game.put("controls", getControlsJson());
			game.put("displayName", getDisplayName());
			game.put("id", getId());
			game.put("interfaces", getInterfacesJson());
			game.put("length", getLength());
			game.put("name", getName());
			game.put("objectiveGroups", getObjectiveGroupsJson());
			game.put("secondsLeft", getSecondsLeft());
			game.put("state", getState());
			game.put("winState", getWinState());

			return game;
		}


		public String toString() {
			return toJson().toJSONString();
		}

	// End representation methods. /////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////
}
