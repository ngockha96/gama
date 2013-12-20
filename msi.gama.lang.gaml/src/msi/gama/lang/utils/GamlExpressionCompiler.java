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
 * - Benoît Gaudou, UMR 5505 IRIT, CNRS/Univ. Toulouse 1 (Documentation, Tests), 2010-2012
 * - Phan Huy Cuong, DREAM team, Univ. Can Tho (XText-based GAML), 2012
 * - Pierrick Koch, UMI 209 UMMISCO, IRD/UPMC (XText-based GAML), 2010-2011
 * - Romain Lavaud, UMI 209 UMMISCO, IRD/UPMC (RCP environment), 2010
 * - Francois Sempe, UMI 209 UMMISCO, IRD/UPMC (EMF model, Batch), 2007-2009
 * - Edouard Amouroux, UMI 209 UMMISCO, IRD/UPMC (C++ initial porting), 2007-2008
 * - Chu Thanh Quang, UMI 209 UMMISCO, IRD/UPMC (OpenMap integration), 2007-2008
 */
package msi.gama.lang.utils;

import static msi.gama.common.interfaces.IKeyword.*;
import static msi.gaml.expressions.IExpressionFactory.*;
import java.io.*;
import java.text.*;
import java.util.*;
import msi.gama.common.interfaces.IGamlIssue;
import msi.gama.common.util.StringUtils;
import msi.gama.kernel.model.IModel;
import msi.gama.lang.gaml.gaml.*;
import msi.gama.lang.gaml.gaml.util.GamlSwitch;
import msi.gama.lang.gaml.resource.GamlResource;
import msi.gama.lang.gaml.validation.GamlJavaValidator;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.*;
import msi.gaml.compilation.AbstractGamlAdditions;
import msi.gaml.descriptions.*;
import msi.gaml.expressions.*;
import msi.gaml.factories.DescriptionFactory;
import msi.gaml.operators.IUnits;
import msi.gaml.statements.DrawStatement;
import msi.gaml.types.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.*;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.resource.*;

/**
 * The Class ExpressionParser.
 */

public class GamlExpressionCompiler implements IExpressionCompiler<Expression> {

	public IVarExpression each_expr;
	final static NumberFormat nf = NumberFormat.getInstance(Locale.US);
	private IExpression world = null;

	/*
	 * The context (IDescription) in which the parser operates. If none is given, the global context
	 * of the current simulation is returned (via simulation.getModel().getDescription()) if it is
	 * available. Otherwise, only simple expressions (that contain constants) can be parsed.
	 */
	private IDescription context;
	private static boolean synthetic;
	private final IExpressionFactory factory;

	static {
		IExpressionCompiler.OPERATORS.put(MY, new GamaMap());
	}

	public GamlExpressionCompiler() {
		factory = GAML.getExpressionFactory();
	}

	@Override
	public void reset() {
		world = null;
		each_expr = null;
		synthetic = false;
	}

	@Override
	public IExpression compile(final IExpressionDescription s, final IDescription parsingContext) {
		if ( s.isConstant() ) { return s.getExpression(); }
		EObject e = getEObjectOf(s);
		IDescription previous = setContext(parsingContext);
		try {
			IExpression result = compile(e);
			return result;
		} finally {
			setContext(previous);
			synthetic = false;
		}

	}

	private IExpression compile(final EObject s) {
		if ( s == null ) {
			// No error, since the null expressions come from previous (more focused)
			// errors and not from the parser itself.
			return null;
		}
		IExpression expr = compiler.doSwitch(s);
		if ( !synthetic ) {
			DescriptionFactory.setGamlDocumentation(s, expr);
		}
		return expr;
	}

	// KEEP
	private IExpression species(final String name) {
		return factory.createConst(name, Types.get(IType.SPECIES), getSpeciesContext(name).getType());
	}

	private IExpression skill(final String name) {
		return factory.createConst(name, Types.get(IType.STRING));
	}

	// KEEP
	private IExpression unary(final String op, final Expression e) {
		if ( op == null ) { return null; }
		IExpression expr = compile(e);
		if ( expr == null ) { return null; }
		if ( op.equals(MY) ) {
			IDescription desc = getContext().getDescriptionDeclaringVar(MYSELF);
			if ( desc != null ) {
				// We are in a remote context, so 'my' refers to the calling agent
				IExpression myself = desc.getVarExpr(MYSELF);
				IDescription species = myself.getType().getSpecies();
				IExpression var = species.getVarExpr(EGaml.getKeyOf(e));
				return factory.createOperator(_DOT, desc, myself, var);
			}
			// Otherwise, we ignore 'my' since it refers to 'self'
			return expr;
		}
		// The unary "unit" operator should let the value of its child pass through
		if ( op.equals("°") || op.equals("#") ) { return expr; }
		if ( isSpeciesName(op) ) { return factory.createOperator(AS, context, expr, species(op)); }
		if ( isSkillName(op) ) { return factory.createOperator(AS, context, expr, skill(op)); }
		return factory.createOperator(op, context, expr);
	}

	private IExpression binary(final String op, final IExpression left, final Expression e2) {
		if ( left == null ) { return null; }
		// if the operator is "as", the right-hand expression should be a casting type
		if ( AS.equals(op) ) {
			String type = EGaml.getKeyOf(e2);
			if ( isSpeciesName(type) ) { return factory.createOperator(op, context, left, species(type)); }
			if ( isSkillName(type) ) { return factory.createOperator(AS_SKILL, context, left, skill(type)); }
			if ( isTypeName(type) ) { return factory.createOperator(type, context, left); }
			getContext().error(
				"'as' must be followed by a type, species or skill name. " + type + " is neither of these.",
				IGamlIssue.NOT_A_TYPE, e2, type);
			return null;
		}
		// if the operator is "is", the right-hand expression should be a type
		if ( IS.equals(op) ) {
			String type = EGaml.getKeyOf(e2);
			if ( isTypeName(type) ) { return factory.createOperator(op, context, left,
				factory.createConst(type, Types.get(IType.STRING))); }
			if ( isSkillName(type) ) { return factory.createOperator(IS_SKILL, context, left,
				factory.createConst(type, Types.get(IType.STRING))); }
			getContext().error(
				"'is' must be followed by a type, species or skill name. " + type + " is neither of these.",
				IGamlIssue.NOT_A_TYPE, e2, type);
			return null;
		}

		// we verify and compile apart the calls to actions as operators
		// TypeDescription sd = getContext().getSpeciesDescription(left.getType().getSpeciesName());
		TypeDescription sd = left.getType().getSpecies();
		if ( sd != null ) {
			StatementDescription action = sd.getAction(op);
			if ( action != null ) {
				IExpression result = action(op, left, e2, action);
				if ( result != null ) { return result; }
			}
		}
		// It is not an action so it must be an operator. We emit an error and stop compiling if it
		// is not
		if ( !OPERATORS.containsKey(op) ) {
			getContext().error("Unknown action or operator: " + op, IGamlIssue.UNKNOWN_ACTION, op);
			return null;
		}

		// if the operator is an iterator, we must initialize the context sensitive "each" variable
		if ( ITERATORS.contains(op) ) {

			IType t = left.getContentType();
			IType ct = left.getElementsContentType();
			IType kt = left.getElementsKeyType();
			each_expr = new EachExpression(t, ct, kt);
		}
		// we can now safely compile the right-hand expression
		IExpression right = compile(e2);
		// and return the binary expression
		return factory.createOperator(op, context, left, right);

	}

	private IExpression action(final String name, final IExpression callee, final EObject args,
		final StatementDescription action) {
		IExpression right = compileArguments(action, args);
		return factory.createAction(name, context, action, callee, right);
	}

	// KEEP
	private IExpression binary(final String op, final Expression e1, final Expression right) {
		// if the expression is " var of agents ", we must compile it apart
		if ( OF.equals(op) ) { return compileFieldExpr(right, e1); }
		// we can now safely compile the left-hand expression
		IExpression left = compile(e1);
		return binary(op, left, right);
	}

	private TypeDescription getSpeciesContext(final String e) {
		return getContext().getSpeciesDescription(e);
	}

	private boolean isSpeciesName(final String s) {
		ModelDescription m = getContext().getModelDescription();
		SpeciesDescription sd = m.getSpeciesDescription(s);
		return sd != null && !(sd instanceof ExperimentDescription);
		// return getContext().getModelDescription().hasSpeciesDescription(s);
	}

	private boolean isSkillName(final String s) {
		return AbstractGamlAdditions.getSkillClasses().containsKey(s);
	}

	private boolean isTypeName(final String s) {
		return getContext().getModelDescription().getTypeNamed(s) != null;
	}

	private IExpression compileFieldExpr(final Expression leftExpr, final Expression fieldExpr) {
		IExpression owner = compile(leftExpr);
		if ( owner == null ) { return null; }
		IType type = owner.getType();
		TypeDescription species = type.getSpecies();
		if ( species == null ) {
			// It can only be a variable as 'actions' are not defined on simple objects, except for matrices, where it
			// can also represent the dot product
			String var = EGaml.getKeyOf(fieldExpr);
			TypeFieldExpression expr = (TypeFieldExpression) type.getGetter(var);
			if ( type.id() == IType.MATRIX && expr == null ) { return binary(".", owner, fieldExpr); }

			if ( expr == null ) {
				species = type.getSpecies();
				context.error("Field " + var + " unknown for type " + type, IGamlIssue.UNKNOWN_FIELD, leftExpr, var,
					type.toString());
				return null;
			}
			expr = (TypeFieldExpression) expr.copy().init(var, context, owner);
			DescriptionFactory.setGamlDocumentation(fieldExpr, expr);
			return expr;
		}
		// We are now dealing with an agent. In that case, it can be either an attribute or an
		// action call
		if ( fieldExpr instanceof VariableRef ) {
			String var = EGaml.getKeyOf(fieldExpr);
			IVarExpression expr = (IVarExpression) species.getVarExpr(var);
			if ( expr == null ) {
				context.error("Unknown variable :" + var + " in " + species.getName(), IGamlIssue.UNKNOWN_VAR,
					leftExpr, var, species.getName());
			}
			DescriptionFactory.setGamlDocumentation(fieldExpr, expr);
			return factory.createOperator(_DOT, context, owner, expr);
		} else if ( fieldExpr instanceof Function ) {
			String name = EGaml.getKeyOf(fieldExpr);
			StatementDescription action = species.getAction(name);
			if ( action != null ) {
				ExpressionList list = ((Function) fieldExpr).getArgs();
				IExpression call =
					action(name, owner, list == null ? ((Function) fieldExpr).getParameters() : list, action);
				DescriptionFactory.setGamlDocumentation(fieldExpr, call); // ??
				return call;
			}
		}
		return null;

	}

	// KEEP
	private IExpression getWorldExpr() {
		if ( world == null ) {
			IType tt = getContext().getModelDescription()./* getWorldSpecies(). */getType();
			world =
				factory.createVar(WORLD_AGENT_NAME, tt, Types.NO_TYPE, Types.get(IType.STRING), true,
					IVarExpression.WORLD, context.getModelDescription());
		}
		return world;
	}

	private IDescription setContext(final IDescription context) {
		IDescription previous = context;
		this.context = context;
		return previous;
	}

	private IDescription getContext() {
		if ( context == null ) { return GAML.getModelContext(); }
		return context;
	}

	// FIXME Possibility to simplify here as we recreate a map that will be later decompiled...
	// Create Arguments directly ?
	private IExpression compileArguments(final StatementDescription action, final EObject args) {
		Map<String, IExpressionDescription> descriptions = parseArguments(action, args, context);
		if ( descriptions == null ) { return null; }
		final GamaList list = new GamaList();
		for ( Map.Entry<String, IExpressionDescription> d : descriptions.entrySet() ) {
			list.add(factory.createOperator("::", context, factory.createConst(d.getKey(), Types.get(IType.STRING)),
				compile(d.getValue(), context)));
		}
		return factory.createMap(list);
	}

	/**
	 * @see msi.gaml.expressions.IExpressionParser#parseArguments(msi.gaml.descriptions.ExpressionDescription,
	 *      msi.gaml.descriptions.IDescription)
	 */
	@Override
	public Map<String, IExpressionDescription> parseArguments(final StatementDescription action, final EObject o,
		final IDescription command) {
		if ( o == null ) { return Collections.EMPTY_MAP; }
		boolean completeArgs = false;
		List<Expression> parameters = null;
		if ( o instanceof Array ) {
			parameters = EGaml.getExprsOf(((Array) o).getExprs());
		} else if ( o instanceof Parameters ) {
			parameters = EGaml.getExprsOf(((Parameters) o).getParams());
		} else if ( o instanceof ExpressionList ) {
			parameters = ((ExpressionList) o).getExprs();
			completeArgs = true;
		} else {
			command.error("Arguments must be written [a1::v1, a2::v2], (a1:v1, a2:v2) or (v1, v2)");
			return null;
		}
		Map<String, IExpressionDescription> argMap = new HashMap();
		List<String> args = action == null ? null : action.getArgNames();

		int index = 0;
		for ( Expression exp : parameters ) {
			String arg = null;
			IExpressionDescription ed = null;
			Set<Diagnostic> errors = new LinkedHashSet();
			if ( exp instanceof ArgumentPair || exp instanceof Parameter ) {
				arg = EGaml.getKeyOf(exp);
				ed = EcoreBasedExpressionDescription.create(exp.getRight(), errors);
			} else if ( exp instanceof Pair ) {
				arg = EGaml.getKeyOf(exp.getLeft());
				ed = EcoreBasedExpressionDescription.create(exp.getRight(), errors);
			} else if ( completeArgs ) {
				if ( args != null && action != null && index == args.size() ) {
					command.error("Wrong number of arguments. Action " + action.getName() + " expects " + args);
					return argMap;
				}
				arg = args == null ? String.valueOf(index++) : args.get(index++);
				ed = EcoreBasedExpressionDescription.create(exp, errors);
			}
			if ( !errors.isEmpty() ) {
				for ( Diagnostic d : errors ) {
					context.warning(d.getMessage(), "", exp);
				}
			}
			argMap.put(arg, ed);
		}
		return argMap;
	}

	GamlSwitch<IExpression> compiler = new GamlSwitch<IExpression>() {

		@Override
		public IExpression caseCast(final Cast object) {
			Expression type = object.getRight();
			IExpression simpleType = binary(AS, compile(object.getLeft()), type);
			if ( simpleType == null ) { return null; }
			if ( type instanceof TypeRef ) {
				Expression first = ((TypeRef) type).getFirst();
				Expression second = ((TypeRef) type).getSecond();
				// No content type, we return the simple expression
				if ( first == null ) { return simpleType; }
				// We compute the content type
				String contentType;
				Expression contentTypeExpression = second == null ? first : second;
				contentType = EGaml.getKeyOf(contentTypeExpression);
				if ( contentType == null || !isTypeName(contentType) ) {
					getContext().error(
						"A type can only contain another type, species or skill name. " + contentType +
							" is neither of these.", IGamlIssue.NOT_A_TYPE, contentTypeExpression, contentType);
					return simpleType;
				}
				return factory.createOperator("containing", context, simpleType,
					factory.createCastingExpression(context.getModelDescription().getTypeNamed(contentType)));
			}
			getContext().error(EGaml.toString(type) + " is not a type name. ", IGamlIssue.NOT_A_TYPE, type);
			return null;
		}

		@Override
		public IExpression caseSkillRef(final SkillRef object) {
			return skill(EGaml.getKey.caseSkillRef(object));
		}

		@Override
		public IExpression caseActionRef(final ActionRef object) {
			return factory.createConst(EGaml.getKey.caseActionRef(object), Types.get(IType.STRING));
		}

		@Override
		public IExpression caseExpression(final Expression object) {
			// in the general case, we try to return a binary expression
			IExpression result = binary(EGaml.getKeyOf(object), object.getLeft(), object.getRight());
			return result;
		}

		@Override
		public IExpression caseVariableRef(final VariableRef object) {
			String s = EGaml.getKey.caseVariableRef(object);
			if ( s == null ) { return caseVarDefinition(object.getRef()); }
			return caseVar(s, object);
		}

		@Override
		public IExpression caseTypeRef(final TypeRef object) {
			String s = EGaml.getKeyOf(object);
			if ( s == null ) {
				// we delegate to the referenced TypeDefinition
				return caseTypeDefinition(object.getRef());
			}
			return caseVar(s, object);
		}

		@Override
		public IExpression caseEquationRef(final EquationRef object) {
			return factory.createConst(EGaml.getKey.caseEquationRef(object), Types.get(IType.STRING));
		}

		@Override
		public IExpression caseUnitName(final UnitName object) {
			String s = EGaml.getKeyOf(object);
			if ( IUnits.UNITS.containsKey(s) ) { return factory.createUnitExpr(s, context); }
			// If it is a unit, we return its float value
			context.error(s + " is not a unit name.", IGamlIssue.NOT_A_UNIT, object, (String[]) null);
			return null;
		}

		@Override
		public IExpression caseVarDefinition(final VarDefinition object) {
			return skill(object.getName());
		}

		@Override
		public IExpression caseTypeDefinition(final TypeDefinition object) {
			return caseVar(object.getName(), object);
		}

		@Override
		public IExpression caseSkillFakeDefinition(final SkillFakeDefinition object) {
			return caseVar(object.getName(), object);
		}

		@Override
		public IExpression caseReservedLiteral(final ReservedLiteral object) {
			return caseVar(EGaml.getKeyOf(object), object);
		}

		@Override
		public IExpression caseIf(final If object) {
			IExpression ifFalse = compile(object.getIfFalse());
			IExpression alt = factory.createOperator(":", context, compile(object.getRight()), ifFalse);
			return factory.createOperator("?", context, compile(object.getLeft()), alt);
		}

		@Override
		public IExpression caseArgumentPair(final ArgumentPair object) {
			IExpression result = binary("::", caseVar(EGaml.getKeyOf(object), object), object.getRight());
			return result;
		}

		@Override
		public IExpression casePair(final Pair object) {
			IExpression result = binary(EGaml.getKeyOf(object), object.getLeft(), object.getRight());
			return result;
		}

		@Override
		public IExpression caseBinary(final Binary object) {
			IExpression result = binary(EGaml.getKeyOf(object), object.getLeft(), object.getRight());
			return result;
		}

		@Override
		public IExpression caseUnit(final Unit object) {
			// We simply return a multiplication, since the right member (the "unit") will be
			// translated into its float value

			// AD: Hack to address Issue 387. If the unit is a pixel, we add +1 to the whole expression.
			IExpression right = compile(object.getRight());
			IExpression result = binary("*", object.getLeft(), object.getRight());
			if ( result != null && ((BinaryOperator) result).right() instanceof PixelUnitExpression ) {
				result = factory.createOperator("+", context, factory.createConst(1, Types.get(IType.INT)), result);
			}
			return result;
		}

		@Override
		public IExpression caseUnary(final Unary object) {
			IExpression result = unary(EGaml.getKeyOf(object), object.getRight());
			return result;
		}

		@Override
		public IExpression caseDot(final Dot object) {
			return compileFieldExpr(object.getLeft(), object.getRight());
		}

		@Override
		public IExpression caseAccess(final Access object) {
			IExpression container = compile(object.getLeft());
			// If no container is defined, return a null expression
			if ( container == null ) { return null; }
			IType contType = container.getType();
			boolean isMatrix = contType.id() == IType.MATRIX;
			IType keyType = container.getKeyType();
			List<? extends Expression> list = EGaml.getExprsOf(object.getArgs());
			List<IExpression> result = new ArrayList();
			int size = list.size();
			for ( int i = 0; i < size; i++ ) {
				Expression eExpr = list.get(i);
				IExpression e = compile(eExpr);
				if ( e != null ) {
					IType elementType = e.getType();
					if ( keyType != Types.NO_TYPE && !keyType.isAssignableFrom(e.getType()) ) {
						if ( !(isMatrix && elementType.id() == IType.INT && size > 1) ) {
							context.warning(
								"a " + contType.toString() + " cannot be accessed using a " + elementType.toString() +
									" index", IGamlIssue.WRONG_TYPE, eExpr);
						}
					}
					result.add(e);
				}
			}
			if ( size > 2 ) {
				int expected = isMatrix ? 2 : 1;
				String end = expected == 1 ? " only 1 index" : " 1 or 2 indices";
				context.warning("a " + contType.toString() + " should be accessed using" + end,
					IGamlIssue.DIFFERENT_ARGUMENTS, object);
			}

			IExpression indices = factory.createList(result);
			return factory.createOperator("internal_at", context, container, indices);
		}

		@Override
		public IExpression caseArray(final Array object) {
			List<? extends Expression> list = EGaml.getExprsOf(object.getExprs());
			List<IExpression> result = new ArrayList();
			boolean allPairs = true;
			for ( int i = 0, n = list.size(); i < n; i++ ) {
				Expression eExpr = list.get(i);
				allPairs = allPairs && eExpr instanceof Pair;
				IExpression e = compile(eExpr);
				result.add(e);
			}
			if ( allPairs && !list.isEmpty() ) { return factory.createMap(result); }
			return factory.createList(result);
		}

		@Override
		public IExpression casePoint(final Point object) {
			IExpression point2d = binary(IExpressionCompiler.INTERNAL_POINT, object.getLeft(), object.getRight());
			Expression z = object.getZ();
			return z == null ? point2d : binary(IExpressionCompiler.INTERNAL_Z, point2d, z);
		}

		@Override
		public IExpression caseParameters(final Parameters object) {
			IList<IExpression> list = new GamaList();
			for ( Expression p : EGaml.getExprsOf(object.getParams()) ) {
				list.add(binary("::", factory.createConst(EGaml.getKeyOf(p.getLeft()), Types.get(IType.STRING)),
					p.getRight()));
			}
			return factory.createMap(list);
		}

		@Override
		public IExpression caseExpressionList(final ExpressionList object) {
			List<Expression> list = EGaml.getExprsOf(object);
			if ( list.isEmpty() ) { return null; }
			if ( list.size() > 1 ) {
				context.warning(
					"A sequence of expressions is not expected here. Only the first expression will be evaluated",
					IGamlIssue.UNKNOWN_ARGUMENT, object);
			}
			IExpression expr = compile(list.get(0));
			return expr;
		}

		@Override
		public IExpression caseFunction(final Function object) {
			String op = EGaml.getKeyOf(object);

			SpeciesDescription sd = context.getSpeciesContext();
			if ( sd != null ) {
				StatementDescription action = sd.getAction(op);
				if ( action != null ) {
					EObject params = object.getParameters();
					if ( params == null ) {
						params = object.getArgs();
					}
					IExpression call = action(op, caseVar(SELF, object), params, action);
					if ( call != null ) { return call; }
				}
			}

			List<Expression> args = EGaml.getExprsOf(object.getArgs());
			int size = args.size();
			if ( size == 1 ) {
				IExpression result = unary(op, args.get(0));
				return result;
			}
			if ( size == 2 ) {
				IExpression result = binary(op, args.get(0), args.get(1));
				return result;
			}
			IExpression[] compiledArgs = new IExpression[size];
			for ( int i = 0; i < size; i++ ) {
				compiledArgs[i] = compile(args.get(i));
			}
			IExpression result = factory.createOperator(op, context, compiledArgs);
			return result;
		}

		@Override
		public IExpression caseIntLiteral(final IntLiteral object) {
			try {
				Integer val = Integer.parseInt(EGaml.getKeyOf(object), 10);
				return factory.createConst(val, Types.get(IType.INT));
			} catch (NumberFormatException e) {
				context.error("Malformed integer: " + EGaml.getKeyOf(object), IGamlIssue.UNKNOWN_NUMBER, object);
				return null;
			}
		}

		@Override
		public IExpression caseDoubleLiteral(final DoubleLiteral object) {
			try {
				String s = EGaml.getKeyOf(object);
				if ( s == null ) { return null; }
				Number val = nf.parse(s);
				return factory.createConst(val.doubleValue(), Types.get(IType.FLOAT));
			} catch (ParseException e) {
				context.error("Malformed float: " + EGaml.getKeyOf(object), IGamlIssue.UNKNOWN_NUMBER, object);
				return null;
			}

		}

		@Override
		public IExpression caseColorLiteral(final ColorLiteral object) {
			try {
				Integer val = Integer.parseInt(EGaml.getKeyOf(object).substring(1), 16);
				return factory.createConst(val, Types.get(IType.INT));
			} catch (NumberFormatException e) {
				context.error("Malformed integer: " + EGaml.getKeyOf(object), IGamlIssue.UNKNOWN_NUMBER, object);
				return null;
			}
		}

		@Override
		public IExpression caseStringLiteral(final StringLiteral object) {
			return factory.createConst(StringUtils.unescapeJava(EGaml.getKeyOf(object)), Types.get(IType.STRING));
		}

		@Override
		public IExpression caseBooleanLiteral(final BooleanLiteral object) {
			String s = EGaml.getKeyOf(object);
			if ( s == null ) { return null; }
			return s.equalsIgnoreCase(TRUE) ? TRUE_EXPR : FALSE_EXPR;
		}

		@Override
		public IExpression defaultCase(final EObject object) {
			if ( !getContext().hasErrors() ) {
				// In order to avoid too many "useless errors"
				getContext().error("Cannot compile: " + object, IGamlIssue.GENERAL, object);
			}
			return null;
		}

	};

	public IExpression caseVar(final String s, final EObject object) {
		if ( s == null ) {
			getContext().error("Unknown variable", IGamlIssue.UNKNOWN_VAR, object);
			return null;
		}

		// if ( s.equals("tototo") ) {
		// GuiUtils.debug("GamlExpressionCompiler.caseVar");
		// }

		// HACK
		if ( s.equals(USER_LOCATION) ) { return factory.createVar(USER_LOCATION, Types.get(IType.POINT),
			Types.get(IType.FLOAT), Types.get(IType.INT), true, IVarExpression.TEMP, context); }

		// HACK
		if ( s.equals(EACH) ) { return each_expr; }
		if ( s.equals(NULL) ) { return IExpressionFactory.NIL_EXPR; }
		// We try to find a species name from the name
		IDescription temp_sd = null;
		if ( context != null ) {
			temp_sd = getContext().getSpeciesDescription(s);
		}
		if ( temp_sd != null ) { return factory.createConst(s, Types.get(IType.SPECIES), temp_sd.getType()); }
		if ( s.equals(SELF) ) {
			temp_sd = getContext().getSpeciesContext();
			if ( temp_sd == null ) {
				context.error("Unable to determine the species of self", IGamlIssue.GENERAL, object);
				return null;
			}
			IType tt = temp_sd.getType();
			return factory.createVar(SELF, tt, Types.NO_TYPE, Types.get(IType.STRING), true, IVarExpression.SELF, null);
		}
		if ( s.equalsIgnoreCase(WORLD_AGENT_NAME) ) { return getWorldExpr(); }

		// By default, we try to find a variable
		// if ( s.equals(MYSELF) ) {
		// GuiUtils.debug("GamlExpressionCompiler.caseVar MYSELF");
		// }

		temp_sd = context == null ? null : context.getDescriptionDeclaringVar(s);
		if ( temp_sd != null ) {
			if ( temp_sd instanceof SpeciesDescription ) {
				SpeciesDescription remote_sd = context.getSpeciesContext();
				if ( remote_sd != null ) {
					SpeciesDescription found_sd = (SpeciesDescription) temp_sd;

					if ( remote_sd != temp_sd && !remote_sd.isBuiltIn() && !remote_sd.hasMacroSpecies(found_sd) ) {
						// GuiUtils.debug("GamlExpressionCompiler.caseVar : var " + s + " used in " + remote_sd +
						// " declared in " + temp_sd);
						context.error(
							"The variable " + s + " is not accessible in this context (" + remote_sd.getName() +
								"), but in the context of " + found_sd.getName() +
								". It should be preceded by 'myself.'", IGamlIssue.UNKNOWN_VAR, object, s);
					}
				}
			}

			return temp_sd.getVarExpr(s);
		}

		if ( isSkillName(s) ) { return skill(s); }

		if ( context != null ) {

			// Short circuiting the use of keyword in "draw ..." to ensure backward
			// compatibility while providing a useful warning.

			if ( context.getKeyword().equals(DRAW) ) {
				if ( DrawStatement.SHAPES.keySet().contains(s) ) {
					context.warning("The symbol " + s +
						" is not used anymore in draw. Please use geometries instead, e.g. '" + s + "(size)'",
						IGamlIssue.UNKNOWN_KEYWORD, object, s);
				}
				return factory.createConst(s + "__deprecated", Types.get(IType.STRING));
			}

			context.error("The variable " + s +
				" is not defined or accessible in this context. Check its name or declare it", IGamlIssue.UNKNOWN_VAR,
				object, s);
		}
		return null;

	}

	private static GamlResource getFreshResource() {
		if ( resource == null ) {
			XtextResourceSet rs = EGaml.getInstance(XtextResourceSet.class);
			rs.setClasspathURIContext(EcoreBasedExpressionDescription.class);
			IResourceFactory resourceFactory = EGaml.getInstance(IResourceFactory.class);
			URI uri = URI.createURI("dummy.gaml");
			resource = (GamlResource) resourceFactory.createResource(uri);
			rs.getResources().add(resource);
		} else {
			resource.unload();
		}
		return resource;
	}

	private static GamlResource resource;

	private static EObject getEObjectOf(final IExpressionDescription s) {
		EObject o = s.getTarget();
		if ( o == null && s instanceof StringBasedExpressionDescription ) {
			synthetic = true;
			o = getEObjectOf(s.toString());
		}
		return o;
	}

	private static Map<String, EObject> cache = new LinkedHashMap();

	private static EObject getEObjectOf(final String string) throws GamaRuntimeException {
		EObject result = cache.get(string);
		if ( result != null ) { return result; }
		long begin = System.nanoTime();
		String s = "dummy <- " + string;
		GamlResource resource = getFreshResource();
		InputStream is = new ByteArrayInputStream(s.getBytes());
		try {
			resource.load(is, null);
		} catch (IOException e1) {
			e1.printStackTrace();
			return null;
		}

		if ( resource.getErrors().isEmpty() ) {
			EObject e = resource.getContents().get(0);
			if ( e instanceof StringEvaluator ) {
				result = ((StringEvaluator) e).getExpr();
			}
		} else {
			Resource.Diagnostic d = resource.getErrors().get(0);
			throw GamaRuntimeException.error(d.getMessage());
		}
		// long end = System.nanoTime();
		// double ms = (end - begin) / 1000000d;
		// count += end - begin;
		// GuiUtils.debug("   -> compilation of " + string + " in " + ms + " ms (Total: " + count / 1000000d + ")");
		if ( result instanceof TerminalExpression ) {
			cache.put(string, result);
		}
		return result;
	}

	//
	//
	// hqnghi 11/Oct/13 two methods for compiling models directly from files
	//
	//
	@Override
	public IModel createModelFromFile(final String fileName) {
		// System.out.println(fileName + " model is loading...");

		GamlResource resource =
			(GamlResource) getContext().getModelDescription().getUnderlyingElement(null).eResource();

		URI iu = URI.createURI(fileName).resolve(resource.getURI());
		IModel lastModel = null;
		ResourceSet rs = new ResourceSetImpl();
		// GamlResource r = (GamlResource) rs.getResource(iu, true);
		GamlResource r = (GamlResource) rs.getResource(iu, true);
		// URI.createURI("file:///" + fileName), true);

		try {
			GamlJavaValidator validator = new GamlJavaValidator();
			// = (GamlJavaValidator) injector
			// .getInstance(EValidator.class);

			lastModel = validator.build(r);
			if ( !r.getErrors().isEmpty() ) {
				lastModel = null;
				// System.out.println("End compilation of " + m.getName());
			}

		} catch (GamaRuntimeException e1) {
			System.out.println("Exception during compilation:" + e1.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// collectErrors(collect);
			// fireBuildEnded(m, lastModel);
		}
		// FIXME Experiment default no longer exists. Needs to specify one name
		// GAMA.controller.newExperiment(IKeyword.DEFAULT, lastModel);
		// System.out.println("Experiment created ");
		return lastModel;
	}

	@Override
	public ModelDescription createModelDescriptionFromFile(final String fileName) {
		System.out.println(fileName + " model is loading...");

		GamlResource resource =
			(GamlResource) getContext().getModelDescription().getUnderlyingElement(null).eResource();

		URI iu = URI.createURI("file:///" + fileName).resolve(resource.getURI());
		ModelDescription lastModel = null;
		ResourceSet rs = new ResourceSetImpl();
		// GamlResource r = (GamlResource) rs.getResource(iu, true);
		GamlResource r = (GamlResource) rs.getResource(iu, true);
		// URI.createURI("file:///" + fileName), true);

		try {
			GamlJavaValidator validator = new GamlJavaValidator();
			// = (GamlJavaValidator) injector
			// .getInstance(EValidator.class);

			// lastModel = validator.validate((Model) r);
			if ( !r.getErrors().isEmpty() ) {
				lastModel = null;
				// System.out.println("End compilation of " + m.getName());
			}

		} catch (GamaRuntimeException e1) {
			System.out.println("Exception during compilation:" + e1.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// collectErrors(collect);
			// fireBuildEnded(m, lastModel);
		}
		// FIXME Experiment default no longer exists. Needs to specify one name
		// GAMA.controller.newExperiment(IKeyword.DEFAULT, lastModel);
		System.out.println("Experiment created ");
		return lastModel;
	}

	//
	// end-hqnghi
	//
}
