/*
 * GAMA - V1.4 http://gama-platform.googlecode.com
 * 
 * (c) 2007-2011 UMI 209 UMMISCO IRD/UPMC & Partners (see below)
 * 
 * Developers :
 * 
 * - Alexis Drogoul, UMI 209 UMMISCO, IRD/UPMC (Kernel, Metamodel, GAML), 2007-2012
 * - Vo Duc An, UMI 209 UMMISCO, IRD/UPMC (SWT, multi-level architecture), 2008-2012
 * - Patrick Taillandier, UMR 6228 IDEES, CNRS/Univ. Rouen (Batch, GeoTools & JTS), 2009-2012
 * - Beno�t Gaudou, UMR 5505 IRIT, CNRS/Univ. Toulouse 1 (Documentation, Tests), 2010-2012
 * - Phan Huy Cuong, DREAM team, Univ. Can Tho (XText-based GAML), 2012
 * - Pierrick Koch, UMI 209 UMMISCO, IRD/UPMC (XText-based GAML), 2010-2011
 * - Romain Lavaud, UMI 209 UMMISCO, IRD/UPMC (RCP environment), 2010
 * - Francois Sempe, UMI 209 UMMISCO, IRD/UPMC (EMF model, Batch), 2007-2009
 * - Edouard Amouroux, UMI 209 UMMISCO, IRD/UPMC (C++ initial porting), 2007-2008
 * - Chu Thanh Quang, UMI 209 UMMISCO, IRD/UPMC (OpenMap integration), 2007-2008
 */
package msi.gama.util;

import java.util.*;
import msi.gama.metamodel.shape.ILocation;
import msi.gama.precompiler.GamlAnnotations.getter;
import msi.gama.precompiler.GamlAnnotations.var;
import msi.gama.precompiler.GamlAnnotations.vars;
import msi.gama.precompiler.*;
import msi.gama.runtime.*;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.matrix.*;
import msi.gaml.operators.Cast;
import msi.gaml.types.IType;

/**
 * The Class GamaMap.
 */
@vars({ @var(name = GamaMap.KEYS, type = IType.LIST, of = ITypeProvider.FIRST_KEY_TYPE),
	@var(name = GamaMap.VALUES, type = IType.LIST, of = ITypeProvider.FIRST_CONTENT_TYPE),
	@var(name = GamaMap.PAIRS, type = IType.LIST, of = IType.PAIR) })
public class GamaMap<K, V> extends LinkedHashMap<K, V> implements IContainer<K, V> {

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return super.entrySet();
	}

	public static final String KEYS = "keys";
	public static final String VALUES = "values";
	public static final String PAIRS = "pairs";

	private static final GamaMap.ToStringProcedure toStringProcedure = new ToStringProcedure();
	private static final GamaMap.ToMatrixProcedure toMatrixProcedure = new ToMatrixProcedure();
	private static final GamaMap.ToReverseProcedure toReverseProcedure = new ToReverseProcedure();

	public static GamaMap with(final IList keys, final IList values) {
		final GamaMap result = new GamaMap(keys.size());
		for ( int i = 0, n = keys.size(); i < n; i++ ) {
			result.put(keys.get(i), values.get(i));
		}
		return result;
	}

	public GamaMap() {}

	public GamaMap(final int capacity) {
		super(capacity);
	}

	public GamaMap(final Map arg0) {
		this(arg0.size());
		putAll(arg0);
	}

	public GamaMap(final GamaPair<K, V> pair) {
		this(1);
		put(pair.key, pair.value);
	}

	@Override
	public GamaList<V> listValue(final IScope scope) {
		final GamaList<V> list = new GamaList(values());
		return list;
	}

	@Override
	public IMatrix matrixValue(final IScope scope) throws GamaRuntimeException {
		toMatrixProcedure.init(scope, size());
		for ( final Map.Entry entry : entrySet() ) {
			toMatrixProcedure.execute(scope, entry.getKey(), entry.getValue());
		}
		return toMatrixProcedure.matrix;
	}

	@Override
	public IMatrix matrixValue(final IScope scope, final ILocation preferredSize) throws GamaRuntimeException {
		return matrixValue(scope);
	}

	@Override
	public String stringValue(final IScope scope) {
		toStringProcedure.string = "";
		for ( final Map.Entry<K, V> entry : entrySet() ) {
			toStringProcedure.execute(entry.getKey(), entry.getValue());
		}
		return toStringProcedure.string;
	}

	@Override
	public String toGaml() {
		return "(" + getPairs().toGaml() + " as map )";
	}

	//
	// @Override
	// public IType type() {
	// return Types.get(IType.MAP);
	// }

	@Override
	public GamaMap mapValue(final IScope scope) {
		return this;
	}

	// @Override
	// @operator(value = { IKeyword.AT }, can_be_const = true, type = IType.NONE)
	// @doc(special_cases =
	// "if it is a map, at returns the value corresponding the right operand as key. If the right operand is not a key of the map, at returns nil")

	public void add(final GamaPair<K, V> v) {
		put(v.getKey(), v.getValue());
	}

	@Override
	public V any(final IScope scope) {
		if ( isEmpty() ) { return null; }
		final V[] array = (V[]) values().toArray();
		final int i = GAMA.getRandom().between(0, array.length - 1);
		return array[i];
	}

	@Override
	public void add(final IScope scope, final K index, final Object value, final Object param, final boolean all,
		final boolean add) {
		if ( index == null ) {
			if ( all ) {
				if ( value instanceof GamaMap ) {
					putAll((GamaMap) value);
				} else if ( value instanceof IContainer ) {
					for ( final Object o : ((IContainer) value).iterable(scope) ) {
						add(scope, null, o, null, false, false);
					}
				} else {
					for ( final Map.Entry e : entrySet() ) {
						e.setValue(value);
					}
				}
			} else {
				if ( value instanceof GamaPair ) {
					final GamaPair<K, V> p = (GamaPair) value;
					// TODO Check type with class cast exception ?
					put(p.getKey(), p.getValue());
				} else if ( value instanceof GamaMap ) {
					putAll((GamaMap) value);
				} else {
					final GamaPair<K, V> p = Cast.asPair(scope, value);
					put(p.getKey(), p.getValue());
				}
			}
		} else {
			// TODO Check type with class cast exception ?
			put(index, (V) value);
		}
	}

	@Override
	public void remove(final IScope scope, final Object index, final Object value, final boolean all) {
		if ( index == null ) {
			if ( all ) {
				if ( value instanceof IContainer ) {
					for ( final Object obj : ((IContainer) value).iterable(scope) ) {
						remove(scope, null, obj, true);
					}
				} else if ( value != null ) {
					remove(value);
				} else {
					clear();
				}
			} else {
				remove(value);
			}
		} else {
			remove(index);
		}
	}

	@Override
	public V first(final IScope scope) {
		final Iterator<Map.Entry<K, V>> it = entrySet().iterator();
		final Map.Entry<K, V> entry = it.hasNext() ? it.next() : null;
		return entry == null ? null : entry.getValue();
	}

	public GamaPair getAtIndex(final Integer index) {
		if ( index >= size() ) { return null; }
		final List<Map.Entry<Object, Object>> list = new GamaList(entrySet());
		final Map.Entry entry = list.get(index);
		return entry == null ? null : new GamaPair(entry.getKey(), entry.getValue());

	}

	@Override
	public V last(final IScope scope) {
		if ( size() == 0 ) { return null; }
		final List<Map.Entry<K, V>> list = new GamaList(entrySet());
		final Map.Entry<K, V> entry = list.get(list.size() - 1);
		return entry == null ? null : entry.getValue();
	}

	//
	// @Override
	// public Object sum(final IScope scope) throws GamaRuntimeException {
	// GamaList l = new GamaList(values());
	// return l.sum(scope);
	// }
	//
	// @Override
	// public Object product(final IScope scope) throws GamaRuntimeException {
	// GamaList l = new GamaList(values());
	// return l.product(scope);
	// }

	@Override
	public int length(final IScope scope) {
		return size();
	}

	//
	// @Override
	// public V max(final IScope scope) throws GamaRuntimeException {
	// GamaList<V> l = new GamaList(values());
	// return l.max(scope);
	// }
	//
	// @Override
	// public V min(final IScope scope) throws GamaRuntimeException {
	// GamaList<V> l = new GamaList(values());
	// return l.min(scope);
	// }

	@Override
	public boolean contains(final IScope scope, final Object o) {
		return containsKey(o) || containsValue(o);
	}

	private static class ToReverseProcedure {

		private GamaMap map;

		public boolean execute(final Object a, final Object b) {
			map.put(b, a);
			return true;
		}

		private void init(final int size) {
			map = new GamaMap(size);
		}

	}

	public static class ToMatrixProcedure {

		public IMatrix matrix;
		private int i;

		public boolean execute(final IScope scope, final Object a, final Object b) throws GamaRuntimeException {
			matrix.set(scope, 0, i, a);
			matrix.set(scope, 1, i, b);
			i++;
			return true;
		}

		public void init(final IScope scope, final int size) {
			matrix = new GamaObjectMatrix(2, size);
			i = 0;
		}

	}

	public static class ToStringProcedure {

		public String string;

		public boolean execute(final Object a, final Object b) {

			final StringBuilder res = new StringBuilder(50);
			res.append(string);
			res.append(a);
			res.append(',');
			res.append(b);
			res.append("; ");
			string = res.toString();
			return true;
		}

	}

	@Override
	public IContainer reverse(final IScope scope) {
		toReverseProcedure.init(size());
		for ( final Map.Entry<K, V> entry : entrySet() ) {
			toReverseProcedure.execute(entry.getKey(), entry.getValue());
		}
		return toReverseProcedure.map;
	}

	@getter("keys")
	public GamaList<K> getKeys() {
		return new GamaList(keySet());
	}

	@getter("values")
	public GamaList<V> getValues() {
		return new GamaList(values());
	}

	@getter("pairs")
	public IList getPairs() {
		// FIXME: in the future, this method will be directly operating upon the entry set (so as to
		// avoir duplications). See GamaPair
		final GamaPairList pairs = new GamaPairList();
		for ( final Map.Entry<K, V> entry : entrySet() ) {
			pairs.add(new GamaPair(entry));
		}
		return pairs;
	}

	@Override
	public GamaMap copy(final IScope scope) {
		final GamaMap result = new GamaMap(this);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see msi.gama.interfaces.IGamaContainer#checkBounds(java.lang.Object)
	 */
	@Override
	public boolean checkBounds(final Object index, final boolean forAdding) {
		return true;
	}

	/**
	 * Returns an iterator that iterates on the list of VALUES (not GamaPairs anymore)
	 * @see java.lang.Iterable#iterator()
	 */
	// @Override
	// public Iterator<V> iterator() {
	// return values().iterator();
	// }

	/*
	 * (non-Javadoc)
	 * 
	 * @see msi.gama.interfaces.IGamaContainer#addAll(msi.gama.interfaces.IGamaContainer,
	 * java.lang.Object)
	 */

	/*
	 * (non-Javadoc)
	 * 
	 * @see msi.gama.interfaces.IGamaContainer#addAll(java.lang.Object,
	 * msi.gama.interfaces.IGamaContainer, java.lang.Object)
	 */

	@Override
	public Object get(final IScope scope, final Object index) throws GamaRuntimeException {
		return get(index);
	}

	@Override
	public boolean isEmpty(final IScope scope) {
		return isEmpty();
	}

	@Override
	public Iterable<V> iterable(final IScope scope) {
		return listValue(scope);
	}

	@Override
	public Object getFromIndicesList(final IScope scope, final IList indices) throws GamaRuntimeException {
		if ( indices == null || indices.isEmpty() ) { return null; }
		return get(scope, indices.get(0));
		// We do not consider the case where multiple indices are used. Maybe could be used in the
		// future to return a list of values ?
	}

	class GamaPairList extends GamaList<Map.Entry<K, V>> implements Set<Map.Entry<K, V>> {

	}

}
