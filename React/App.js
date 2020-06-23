class App extends React.Component {

	// Normally the constructor and binding can be left out, but that doesn't seem to work when passing functions into other components and 'this' is not defined when the parent function is called from a child).  Child component also had to be a class component and not a functional component.
	constructor() {

		super()

		this.state = {
			addMinutes: new Map(),
			controlValues: new Map(),
			createGameValue: "",
			games: [],
			gameServerUrl: "http://192.168.1.101:8081"
		}
		this.decrementActiveGameSeconds = this.decrementActiveGameSeconds.bind(this)

		this.timer = setInterval(this.decrementActiveGameSeconds, 1000)

		this.createGame = this.createGame.bind(this)
		this.getGames = this.getGames.bind(this)
		this.getNextGameId = this.getNextGameId.bind(this)
		this.setCreateGame = this.setCreateGame.bind(this)

		this.addMinutes = this.addMinutes.bind(this)
		this.completeGame = this.completeGame.bind(this)
		this.completeObjective = this.completeObjective.bind(this)
		this.completeObjectiveGroup = this.completeObjectiveGroup.bind(this)
		this.pauseGame = this.pauseGame.bind(this)
		this.removeGame = this.removeGame.bind(this)
		this.resumeGame = this.resumeGame.bind(this)
		this.resetObjective = this.resetObjective.bind(this)
		this.resetObjectiveGroup = this.resetObjectiveGroup.bind(this)
		this.sendControl = this.sendControl.bind(this)
		this.setAddMinutes = this.setAddMinutes.bind(this)
		this.setControl = this.setControl.bind(this)
	}

	// Load the list of games when the component mounts, which is the same function that gets run when clicking the refresh games button.
	componentDidMount() {
		this.getGames()
	}

	decrementActiveGameSeconds() {

		let activeGames = [];

		this.state.games.map((game, index) => {

			if (game.state == "started") {
				activeGames.push(index)
			}
		})

		// If there are active games to update the timer for, then adjust the seconds left in the state for those games.
		if (activeGames.length > 0) {

			this.setState(previousState => {

				const currentGames = previousState.games
				
				activeGames.map((game, index) => {
					currentGames[index].secondsLeft--
				})

				return {
					games: currentGames
				}
			})
		}
	}

	// Load the list of games from the game server.
	getGames() {

		fetch(this.state.gameServerUrl, { body: "list games", method: "POST" })
			.then(response => response.json())
			.then(response => {

				// If games were returned, then set them in the state.
				if (Array.isArray(response.games)) {
					this.setState({ games: response.games })
				}
			})
	}

	// Retrieve the next game ID.  This will eventually come from a database listing.
	getNextGameId() {
		return this.state.games.length
	}

	// Create game elements from the current state inside the game listing pane.
	createGameListing() {

		if (this.state.games.length == 0) {
			return (
				<div class="text-center">No games found.</div>
			)
		}

		else {

			// Render out each game if any exist.
			return this.state.games.map(game => 
				<Game
					addMinutesValue={this.state.addMinutesValue}
					completeObjective={this.completeObjective}
					functions={
						{
							addMinutes: this.addMinutes,
							completeGame: this.completeGame,
							completeObjective: this.completeObjective,
							completeObjectiveGroup: this.completeObjectiveGroup,
							pauseGame: this.pauseGame,
							removeGame: this.removeGame,
							resetObjective: this.resetObjective,
							resetObjectiveGroup: this.resetObjectiveGroup,
							resumeGame: this.resumeGame,
							sendControl: this.sendControl,
							setAddMinutes: this.setAddMinutes,
							setControl: this.setControl							
						}
					}
					game={game}
				/>
			)
		}
	}

	// Set the value of the create game select.
	setCreateGame(inputGameValue) {
		this.setState({ createGameValue: inputGameValue })
	}

	// Set the value of the create game select.
	setAddMinutes(inputGameId, inputAddMinutesValue) {

		if (Number.isInteger(parseInt(inputAddMinutesValue))) {

			this.setState((previousState) => {

				let newAddMinutes = previousState.addMinutes

				return { addMinutes: newAddMinutes.set("gameId-" + inputGameId, inputAddMinutesValue) }
			})
		}
	}

	// Add minutes from a minute input to a game.
	addMinutes(inputGameId) {

		const addMinutesValue = this.state.addMinutes.get("gameId-" + inputGameId) * 60

		fetch(this.state.gameServerUrl, { body: "addSeconds " + inputGameId + " " + addMinutesValue, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Create a new game with the state value of the 
	createGame() {

		fetch(this.state.gameServerUrl, { body: "create " + this.state.createGameValue + " " + this.getNextGameId(), method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Complete an objective.
	completeObjective(inputGameId, inputObjectiveName) {

		fetch(this.state.gameServerUrl, { body: "completeObjective " + inputGameId + " " + inputObjectiveName, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Reset an objective.
	resetObjective(inputGameId, inputObjectiveName) {

		fetch(this.state.gameServerUrl, { body: "resetObjective " + inputGameId + " " + inputObjectiveName, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Complete an objective.
	completeObjectiveGroup(inputGameId, inputObjectiveGroupName) {

		fetch(this.state.gameServerUrl, { body: "completeObjectiveGroup " + inputGameId + " " + inputObjectiveGroupName, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Complete an objective.
	resetObjectiveGroup(inputGameId, inputObjectiveGroupName) {

		fetch(this.state.gameServerUrl, { body: "resetObjectiveGroup " + inputGameId + " " + inputObjectiveGroupName, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Send a remove game command to the game server for a particular game ID.
	removeGame(inputGameId) {

		fetch(this.state.gameServerUrl, { body: "remove " + inputGameId, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Send a pause game command to the game server for a particular game ID.
	pauseGame(inputGameId) {

		fetch(this.state.gameServerUrl, { body: "pause " + inputGameId, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Send a resume game command to the game server for a particular game ID.
	resumeGame(inputGameId) {

		fetch(this.state.gameServerUrl, { body: "resume " + inputGameId, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Complete a game by setting its complete status to true.
	completeGame(inputGameId) {

		fetch(this.state.gameServerUrl, { body: "complete " + inputGameId, method: "POST" })
			.then(response => response.json())
			.then(response => {
				this.getGames()
			})
	}

	// Maintain the control value state from all of the controls for a game.
	setControl(inputGameId, inputControlName, inputControlValue) {

		this.setState((previousState) => {

			let newControlValues = previousState.controlValues

			return { controlValues: newControlValues.set("gameId-" + inputGameId + "-" + inputControlName, inputControlValue) }
		})
	}

	// Send a control value for a game.
	sendControl(inputGameId, inputControlName) {

		let controlValue = this.state.controlValues.get("gameId-" + inputGameId + "-" + inputControlName)

		if (controlValue) {

			fetch(this.state.gameServerUrl, { body: "control " + inputGameId + " " + inputControlName + " " + controlValue, method: "POST" })
				.then(response => response.json())
				.then(response => {
					this.getGames()
				})
		}
	}

	render() {

		return (
			<div class="card">

				<div class="card-header">
					<GameBar
						createGame={this.createGame}
						createGameValue={this.state.createGameValue}
						getGames={this.getGames}
						setCreateGame={this.setCreateGame}
					/>
				</div>

				<div class="card-body">
					{this.createGameListing()}
				</div>

			</div>
		)
	}
}