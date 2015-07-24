/*******************************************************************************
 * Copyright (c) 2013 SCI Institute, University of Utah.
 * All rights reserved.
 *
 * License for the specific language governing rights and limitations under Permission
 * is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, 
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, 
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions: The above copyright notice 
 * and this permission notice shall be included in all copies  or substantial portions of the Software. 
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
 *  INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR 
 *  A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR 
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER 
 *  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *     Yarden Livnat  
 *******************************************************************************/
package edu.utah.sci.cyclist.core.ui.views;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import edu.utah.sci.cyclist.Cyclist;
import edu.utah.sci.cyclist.core.event.dnd.DnD;
import edu.utah.sci.cyclist.core.model.CyclistDatasource;
import edu.utah.sci.cyclist.core.model.Field;
import edu.utah.sci.cyclist.core.model.Filter;
import edu.utah.sci.cyclist.core.model.Schema;
import edu.utah.sci.cyclist.core.model.Simulation;
import edu.utah.sci.cyclist.core.model.Table;
import edu.utah.sci.cyclist.core.model.TableRow;
import edu.utah.sci.cyclist.core.model.ValueFilter;
import edu.utah.sci.cyclist.core.model.proxy.TableProxy;
import edu.utah.sci.cyclist.core.ui.components.CyclistViewBase;
import edu.utah.sci.cyclist.core.util.AwesomeIcon;
import edu.utah.sci.cyclist.core.util.GlyphRegistry;
import edu.utah.sci.cyclist.core.util.QueryBuilder;

public class SimpleTableView extends CyclistViewBase {
	public static final String ID = "table-view";
	public static final String TITLE = "Table";
	
	public static final String SIMULATION_FIELD_NAME = "SimID";
	static final Logger log = LogManager.getLogger(SimpleTableView.class.getName());

	private TableView<TableRow> _tableView;
	private Table _currentTable = null;
	private Field _simField; 
	private Filter _simFilter; 
	
	public SimpleTableView() {
		super();
		build();
		addListeners();
		setupActions();
	}
	
	private void build() {
		setTitle(null);
		
		_tableView = new TableView<TableRow>();
		_tableView.getStyleClass().add("simple-table-view");
		_tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		_tableView.getSelectionModel().setCellSelectionEnabled(true);
		setContent(_tableView);
		VBox.setVgrow(_tableView, Priority.NEVER);
	}
	
	private void setupActions() {
		List<Node> actions = new ArrayList<>();
		actions.add(createExportActions());
		addActions(actions);
	}
	
	private Node createExportActions() {
		final Button button = new Button("Export", GlyphRegistry.get(AwesomeIcon.CARET_DOWN));
		button.getStyleClass().add("flat-button");

		// create menu
		final ContextMenu contextMenu = new ContextMenu();
		
		// csv chart
		MenuItem item = new MenuItem("Export csv");
		item.setOnAction(new EventHandler<ActionEvent>() {		
			@Override
			public void handle(ActionEvent event) {
				export();
			}
		});
		contextMenu.getItems().add(item);
		button.setOnMousePressed(new EventHandler<Event>() {
			@Override
			public void handle(Event event) {
				contextMenu.show(button, Side.BOTTOM, 0, 0);
			}
		});
		
		return button;
	}
	
	private void export() {
		if (_currentTable == null) return;
		
		FileChooser chooser = new FileChooser();
		chooser.getExtensionFilters().add( new FileChooser.ExtensionFilter("CSV file (*.csv)", "*.csv") );
		File file = chooser.showSaveDialog(Cyclist.cyclistStage);
		if (file != null) {
			try {
	            FileWriter f = new FileWriter(file);
	            
	            List<Integer> cols = new ArrayList<>();
	            Schema schema = _currentTable.getSchema();	
	            int len = schema.size();
	            for (int i=0; i<len; i++) {
					Field field = schema.getField(i);	
					if (!field.isHidden()) {
						f.write(field.getName());
						f.write( i < len-1 ? ", " : "\n");
						cols.add(i);
					}
				}
	            
    			for (TableRow row : _tableView.itemsProperty().get()) {
    				boolean first = true;
    				for (int c : cols) {
    					if (first) first = false;
    					else f.write(", "); 
    					f.write(row.value[c].toString());
    				}
    				f.write("\n");
    			}
    			f.close();
			} catch (IOException e) {
	            log.error("Error: Can not write to file ["+e.getMessage()+"]");
            }
		}
	}
	
	private void addListeners() {
		InvalidationListener listener = new InvalidationListener() {	
			@Override
			public void invalidated(Observable observable) {
				fetchRows();
			}
		};
		
		filters().addListener(new ListChangeListener<Filter>() {

			@Override
			public void onChanged(ListChangeListener.Change<? extends Filter> change) {
				boolean update = false;
				
				CyclistDatasource ds = getAvailableDatasource();
				
				while (change.next()) {
					for (Filter f : change.getRemoved()) {
						update = update || f.isActive();
						f.removeListener(listener);
					}
					for (Filter f : change.getAddedSubList()) {
						//For a new filter - add the current data source as its data source.
						f.setDatasource(ds);
							
						update = update || f.isActive();
						f.addListener(listener);
					}
				}

				if (update) {
					fetchRows();
				}
			}
		});

		remoteFilters().addListener(new ListChangeListener<Filter>() {

			@Override
			public void onChanged(ListChangeListener.Change<? extends Filter> change) {
				while (change.next()) {
					for (Filter filter : change.getRemoved()) {
						filter.removeListener(listener);
					}
					for (Filter filter : change.getAddedSubList()) {
						filter.addListener(listener);
					}
				}
				fetchRows();
			}
		});
		
	}
	
	/*
	 * Tries to find an available data source either from the current table or the current simulation.
	 * @return CyclistDatasource - the data source which has been found.
	 */
	private CyclistDatasource getAvailableDatasource(){
		Simulation currentSim = getCurrentSimulation();
		
		CyclistDatasource ds = (_currentTable != null && _currentTable.getDataSource()!= null) ? 
				_currentTable.getDataSource() : 
				currentSim != null ? currentSim.getDataSource() : null;
		return ds;		
	}
	
	@Override
	public void selectTable(Table table, boolean active) {
		super.selectTable(table, active);

		if (!active && table != _currentTable) {
			// ignore
			return;
		}
		
		if (active) {
			_currentTable = table;	
			_simField = table.getField(SIMULATION_FIELD_NAME, false);
			Simulation sim = getCurrentSimulation(); 
			if(sim != null){
				_simFilter = new ValueFilter(_simField, sim.getSimulationId());
			}
		} else {
			_currentTable = null;
			_simField = null;
		}

		loadTable(true);
	}
	
	/**
	 * Creates a new filter which keeps only the rows with simulation id that matches the selected simulation id.
	 * @param Simulation sim - the selected simulation.
	 * @param boolean active
	 * 
	 */
	@Override
	public void selectSimulation(Simulation sim, boolean active) {
		super.selectSimulation(sim, active);
		
		_simFilter = null;
		if (getCurrentSimulation() != null) {
			if (_simField != null) {
				_simFilter = new ValueFilter(_simField, sim.getSimulationId());
			}
		}
		
		if (!updateFilters() && _currentTable != null) {
			loadTable(false);
		}

	}
	
	/*
	 * Updates the local filters data source, on a simulation change.
	 */
	private boolean updateFilters() {
		CyclistDatasource ds = getAvailableDatasource();
		
		if (ds != null){
			for (Filter filter : filters()) {
				filter.setDatasource(ds);
			}
		}
		return ds!=null && filters().size() > 0; 
	}
	
	private void loadTable(boolean updateColumns) {
		_tableView.itemsProperty().unbind();
		
		//Clear the table previous data.
		if(_tableView.getItems() != null){
			_tableView.getItems().clear();
		}
		
		if (_currentTable == null || updateColumns) _tableView.getColumns().clear();
		
		if (_currentTable != null && updateColumns) {
			//TODO: this be done only if the table is active
			Schema schema = _currentTable.getSchema();	
			
			List<TableColumn<TableRow, Object>> cols = new ArrayList<>();
			for (int f=0; f<schema.size(); f++) {
				Field field = schema.getField(f);				
				cols.add(createColumn(field, f, field.isHidden()));
			}
			
			_tableView.getColumns().addAll(cols);
		}
		
		if (_currentTable != null) 
			fetchRows();

	}
	
	private String buildQuery() {
		List<Filter> filtersList = new ArrayList<Filter>();
		
		if (_simFilter != null)
			filtersList.add(_simFilter);
		
		//Check the filters current validity
		for(Filter filter : filters()){
			if(_currentTable.hasField(filter.getField())){
				filtersList.add(filter);
			}
		}

		//Check the remote filters current validity
		for(Filter filter : remoteFilters()){
			if(_currentTable.hasField(filter.getField())){
				filtersList.add(filter);
			}
		}
			
		// build the query
		QueryBuilder builder = _currentTable.queryBuilder()
			.filters(filtersList);
		
		log.debug("TableView Query: "+builder.toString());
		
		return builder.toString();
	}
	
	private void fetchRows() {	
		Simulation currentSim = getCurrentSimulation();
		if (_currentTable == null 
				|| (_currentTable.getDataSource() == null && currentSim == null)) 
		{
			return;
		}
		
		final String query = buildQuery();
		
		Task<ObservableList<TableRow>> task = new Task<ObservableList<TableRow>>() {

			@Override
			protected ObservableList<TableRow> call() throws Exception {
				TableProxy proxy = new TableProxy(_currentTable);
				CyclistDatasource ds = currentSim != null? currentSim.getDataSource() : null;
				
				return proxy.getRows(ds, query, 10000);
			}
		};
		
		setCurrentTask(task);
		_tableView.itemsProperty().bind( task.valueProperty());	
		
		Thread th = new Thread(task);
		th.setDaemon(true);
		th.start();
	}
	
	private TableColumn<TableRow, Object> createColumn(final Field field, final int col, final Boolean isHidden) {
				
		TableColumn<TableRow, Object> tc = new TableColumn<>();
		tc.setText(field.getName());
		tc.setVisible(!isHidden);
		
		tc.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<TableRow,Object>, ObservableValue<Object>>() {
			@Override
			public ObservableValue<Object> call(CellDataFeatures<TableRow, Object> cell) {
				return new SimpleObjectProperty<Object>(cell.getValue(), field.getName()) {		
					@Override
					public Object getValue() {
						TableRow row = (TableRow) getBean();
						return row.value[col];
					}
				};
			}
		});		
		
		tc.setCellFactory(new Callback<TableColumn<TableRow, Object>, TableCell<TableRow, Object>>() {

			@Override
			public TableCell<TableRow, Object> call(TableColumn<TableRow, Object> arg0) {
				return new GenericCell<Object>(field);
			}
			
		});
		
		return tc;
					
	}
	
	class GenericCell<T> extends TableCell<TableRow, T> {
		private Field _field; 
		private Label _label;

		public GenericCell(Field field) {
			_field = field;
			_label = new Label("");
			_label.setAlignment(Pos.CENTER);
			setGraphic(_label);
			
			_label.setOnDragDetected(new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					DnD.LocalClipboard clipboard = DnD.getInstance().createLocalClipboard();
//					clipboard.put(DnD.DnD_SOURCE_FORMAT, DnDSource.class, DnDSource.VALUE);
					clipboard.put(DnD.VALUE_FORMAT, Object.class, getItem()); // fix the class
					clipboard.put(DnD.FIELD_FORMAT, Field.class, _field);
					clipboard.put(DnD.TABLE_FORMAT, Table.class, _currentTable);
					
					Dragboard db = _label.startDragAndDrop(TransferMode.COPY);
					
					ClipboardContent content = new ClipboardContent();
					content.putString("value dnd"); // fix: do we need this?
					
					SnapshotParameters snapParams = new SnapshotParameters();
//		            snapParams.setFill(Color.TRANSPARENT);
		            snapParams.setFill(Color.AQUA);
		            
		            content.putImage(_label.snapshot(snapParams, null));	            
					
					db.setContent(content);
					event.consume();
				}
			});
		}
		
		@Override
		protected void updateItem(T item, boolean empty){
			super.updateItem(item,empty);
			
			_label.setText(item != null ? item.toString() : "");
		}
	}
}
