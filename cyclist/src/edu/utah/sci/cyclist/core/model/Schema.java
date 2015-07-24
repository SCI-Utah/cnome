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
package edu.utah.sci.cyclist.core.model;

import java.util.List;
import java.util.UUID;
import java.util.Vector;

import edu.utah.sci.cyclist.core.controller.IMemento;
import edu.utah.sci.cyclist.core.model.DataType.Role;
import edu.utah.sci.cyclist.core.util.SQL;



public class Schema implements Resource {

	private String _id = UUID.randomUUID().toString();
	private Table _table;
	private Vector<Field> _fields = new Vector<Field>();
	
	public Schema(Table table) {
		_table = table;
	}
	
	public String getUID() {
		return _id;
	}
	
	public Table getTable() {
		return _table;
	}
	
	
	public int size() {
		return _fields.size();
	}
	
	public Field getField(int index) {
		return _fields.get(index);
	}
	
	public Field getField(String name) {
		for (Field field : _fields)
			if (field.getName().equals(name)) 
				return field;
		return null;
	}
	
	public Field getField(String name, Boolean casesensetive) {
		if (casesensetive) return getField(name);
		name = name.toLowerCase();
		for (Field field : _fields)
			if (field.getName().toLowerCase().equals(name)) 
				return field;
		return null;
	}
	public void addField(Field field) {
		field.setTable(_table);
		_fields.add(field);
	}
	
	public void set(List<Field> list) {
		_fields.clear();
		_fields.addAll(list);
	}

	public boolean contain(Field field) {
		for (Field f : _fields) {
			if (f.similar(field))
				return true;
		}
		return false;
	}
	
	public void update() {
		for (Field field : _fields) {
			updateField(field);
		}
	}
	
	public void updateField(Field field) {
		// set field type

		String remote_type_name = field.getString(FieldProperties.REMOTE_DATA_TYPE_NAME);
		field.set(FieldProperties.DATA_TYPE_NAME, remote_type_name);
		
//		int remote_type = (int) field.get(FieldProperties.REMOTE_DATA_TYPE);
		DataType.Type type = SQL.fromSQL(remote_type_name);
		
		DataType dataType = new DataType(type);
		field.setDataType(dataType);
		
		if (dataType.getRole() == Role.MEASURE)
			field.set(FieldProperties.AGGREGATION_DEFAULT_FUNC, SQL.DEFAULT_FUNCTION);
//		System.out.println("Field "+field.getName()+"  remote type:"+ remote_type_name+" ["+remote_type+"] type:"+type);
	}
	

	// Save the schema
	public void save(IMemento memento) {
		for(Field field: _fields){
			field.save(memento.createChild("field"));
		}
	}

	// Restore the schema
	public void restore(IMemento memento, Context ctx) {
			// Restore each field
		IMemento[] list = memento.getChildren("field");
		for(IMemento fieldMemento: list){
			Field field = new Field();
			field.setTable(_table);
			field.restore(fieldMemento, ctx);
			addField(field);
		}
	}
	
	/*
	 * Restores the schemas of tables, defined in the simulation configuration file.
	 */
	public void restoreSimulated(IMemento memento, Context ctx) {
		_id = memento.getID();

		// Restore each field
		IMemento[] list = memento.getChildren("field");
		for(IMemento fieldMemento: list){
			Field field = new Field();
			field.setTable(_table);
			field.createFromConfig(fieldMemento, ctx);
			addField(field);
		}
	}
}
