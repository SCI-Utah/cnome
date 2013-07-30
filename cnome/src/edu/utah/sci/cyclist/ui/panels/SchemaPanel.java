package edu.utah.sci.cyclist.ui.panels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.mo.closure.v1.Closure;

import edu.utah.sci.cyclist.Resources;
import edu.utah.sci.cyclist.event.dnd.DnD;
import edu.utah.sci.cyclist.model.DataType;
import edu.utah.sci.cyclist.model.Field;

public class SchemaPanel extends TitledPanel {
		
	private String _id;
	private ObservableList<Field> _fields;
	private List<Entry> _entries;
	private Closure.V1<Field> _onFieldDropAction = null;
	
	public SchemaPanel(String title) {
		super(title);
		_id = title;
		addListeners();
	}
	
	public void setOnFieldDropAction(Closure.V1<Field> action) {
		_onFieldDropAction = action;
	}
	
	public void setFields(ObservableList<Field> fields) {
		if (_fields != fields) {
			if (_fields != null) {
				_fields.removeListener(_invalidationListener);
			}
			
			_fields = fields;
			_fields.addListener(_invalidationListener);
		}
		
		resetContent();
	}
	
	private void resetContent() {
		VBox vbox = (VBox) getContent();
		vbox.getChildren().clear();
		
		SortedList<Field> sorted = _fields.sorted(new Comparator<Field>() {

			@Override
			public int compare(Field o1, Field o2) {
				return o1.getName().compareToIgnoreCase(o2.getName());
			}

			@Override
			public Comparator<Field> reverseOrder() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Comparator<Field> thenComparing(
					Comparator<? super Field> other) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public <U extends Comparable<? super U>> Comparator<Field> thenComparing(
					Function<? super Field, ? extends U> keyExtractor) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Comparator<Field> thenComparing(
					ToIntFunction<? super Field> keyExtractor) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Comparator<Field> thenComparing(
					ToLongFunction<? super Field> keyExtractor) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Comparator<Field> thenComparing(
					ToDoubleFunction<? super Field> keyExtractor) {
				// TODO Auto-generated method stub
				return null;
			}
		});
		
		_entries = new ArrayList<>();
		for (Field field : sorted) {
			Entry entry = createEntry(field);
			_entries.add(entry);
			vbox.getChildren().add(entry.label);
		}
	}
	
	private Entry createEntry(Field field) {
		final Entry entry = new Entry();
		entry.field = field;
		
		entry.label = new Label(field.getName(),new ImageView(Resources.getIcon(_type2Icon.get(field.getType()))));
		
		entry.label.setOnDragDetected(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent event) {					
				
				DnD.LocalClipboard clipboard = DnD.getInstance().createLocalClipboard();
				clipboard.put(DnD.FIELD_FORMAT, Field.class, entry.field);
				clipboard.put(DnD.DnD_SOURCE_FORMAT, Node.class, SchemaPanel.this);
				
				Dragboard db = entry.label.startDragAndDrop(TransferMode.COPY);
				
				ClipboardContent content = new ClipboardContent();
				content.putString(_id);
				
				SnapshotParameters snapParams = new SnapshotParameters();
//	            snapParams.setFill(Color.TRANSPARENT);
	            snapParams.setFill(Color.AQUA);
	            
	            content.putImage(entry.label.snapshot(snapParams, null));	            
				
				db.setContent(content);
				event.consume();
			}
		});
		
		entry.label.setOnDragDone(new EventHandler<DragEvent>() {
			@Override
			public void handle(DragEvent event) {			
			}
		});
		return entry;
	}
	

	private InvalidationListener _invalidationListener = new InvalidationListener() {
		
		@Override
		public void invalidated(Observable observable) {
			resetContent();
		}
	};
	
	
	private void addListeners() {
		getPane().setOnDragEntered(new EventHandler<DragEvent>() {
			public void handle(DragEvent event) {		
				event.consume();
			}
		});
		
		getPane().setOnDragOver(new EventHandler<DragEvent>() {
			public void handle(DragEvent event) {
				DnD.LocalClipboard clipboard = getLocalClipboard();
				
				Node src = clipboard.get(DnD.DnD_SOURCE_FORMAT, Node.class);
				if (src != null && src != SchemaPanel.this) {
					event.acceptTransferModes(TransferMode.COPY);
				}
				event.consume();
			}
		});
		
		getPane().setOnDragExited(new EventHandler<DragEvent>() {
			public void handle(DragEvent event) {
				event.consume();
			}
		});
			
		getPane().setOnDragDropped(new EventHandler<DragEvent>() {
			public void handle(DragEvent event) {
				DnD.LocalClipboard clipboard = getLocalClipboard();
				Field field = clipboard.get(DnD.FIELD_FORMAT, Field.class);
				if (_onFieldDropAction != null) 
					_onFieldDropAction.call(field);
				event.setDropCompleted(true);
			}
		});
	}
	
	
	private DnD.LocalClipboard getLocalClipboard() {
		return DnD.getInstance().getLocalClipboard();
	}
	
	class Entry {
		Label label;
		Field field;
	}
	
	private static Map<DataType.Type, String> _type2Icon = new HashMap<>();
	
	static {
		_type2Icon.put(DataType.Type.NUMERIC, "field/number");
		_type2Icon.put(DataType.Type.TEXT, "field/Ab");
		_type2Icon.put(DataType.Type.DATE, "field/date");
		_type2Icon.put(DataType.Type.DATETIME, "field/date");		
	}
}
