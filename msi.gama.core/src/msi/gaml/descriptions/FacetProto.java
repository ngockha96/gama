package msi.gaml.descriptions;

import msi.gama.common.interfaces.IKeyword;
import msi.gama.precompiler.JavaWriter;
import msi.gaml.types.IType;

public class FacetProto {

	public final String name;
	public final int[] types;
	public final boolean optional;
	public final boolean isLabel;
	public final String[] values;
	public String doc = "No documentation yet";
	static FacetProto KEYWORD = KEYWORD();
	static FacetProto DEPENDS_ON = DEPENDS_ON();
	static FacetProto NAME = NAME();

	public FacetProto(final String name, final int[] types, final String[] values, final boolean optional,
		final String doc) {
		this.name = name;
		this.types = types;
		this.optional = optional;
		isLabel = SymbolProto.ids.contains(types[0]);
		this.values = values;
		if ( doc != null ) {
			int index = doc.indexOf(JavaWriter.DOC_SEP);
			if ( index == -1 ) {
				this.doc = doc;
			} else {
				this.doc = doc.substring(0, index);
			}
		}
	}

	static FacetProto DEPENDS_ON() {
		return new FacetProto(IKeyword.DEPENDS_ON, new int[] { IType.LIST }, new String[0], true,
			"the dependencies of expressions (internal)");
	}

	static FacetProto KEYWORD() {
		return new FacetProto(IKeyword.KEYWORD, new int[] { IType.ID }, new String[0], true,
			"the declared keyword (internal)");
	}

	static FacetProto NAME() {
		return new FacetProto(IKeyword.NAME, new int[] { IType.LABEL }, new String[0], true,
			"the declared name (internal)");
	}
}