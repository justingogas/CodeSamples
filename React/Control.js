class Control extends React.Component {
	
	constructor(props) {
		super(props)
		this.props = props
		
		this.handleControlChange = this.handleControlChange.bind(this)
		this.handleControlClick = this.handleControlClick.bind(this)
	}

	handleControlChange(event) {
		this.props.functions.setControl(
			event.target.attributes["data-gameId"].value,
			event.target.name,
			event.target.value
		)
	}

	handleControlClick(event) {
		this.props.functions.sendControl(
			event.target.attributes["data-gameId"].value,
			event.target.attributes["data-controlName"].value
		)
	}

	createControls(gameId, controls) {

		if (!Array.isArray(controls) || controls.length == 0) {
			return (
				<tr>
					<td colspan="4" align="center">
						No controls found.
					</td>
				</tr>
			)
		}

		else {

			return (
				<React.Fragment>
					{
						controls.map(control => {
							return (
								<React.Fragment>
									<tr name="controlRowReference" class={ !control.active ? "controlInactive" : "" }>
										<td name="controlName" class="col-md-2">{control.displayName}</td>
										<td name="controlDescription" class="col-md-5">{control.description}</td>
										<td class="col-md-5">
											<div class="input-group">
												<select name={control.name} data-gameId={gameId} data-controlId={control.id} class="form-control" onChange={this.handleControlChange}>
													<option value="" selected></option>
													{
														control.states.map(state => {
															return (
																<React.Fragment>
																	<option value={state.value}>{state.name}{ state.description != "" ? " - " + state.description : "" }</option>
																</React.Fragment>
															)
														})
													}
												</select>
												<div class="input-group-append">
													<button disabled={ control.active ? "" : "disabled" } name="controlButton" class="btn btn-primary" type="button" data-controlType="select" data-gameId={gameId} data-controlId="" data-controlName={control.name} onClick={this.handleControlClick}>Send</button>
												</div>
											</div>
										</td>
									</tr>
								</React.Fragment>
							)
						})
					}
				</React.Fragment>
			)
		}
	}

	render() {

		return (

			<div name="controlContainer">

				<table class="table table-bordered">
					<thead>
						<th>Control name</th>
						<th>Description</th>
						<th>Controls</th>
					</thead>
					<tbody name="controlList">
						{this.createControls(this.props.gameId, this.props.controls)}
					</tbody>
					<tfoot></tfoot>
				</table>

			</div>		
		)
	}
}