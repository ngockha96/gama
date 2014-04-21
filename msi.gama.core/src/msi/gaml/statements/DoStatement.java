/*********************************************************************************************
 * 
 * 
 * 'DoStatement.java', in plugin 'msi.gama.core', is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2014 UMI 209 UMMISCO IRD/UPMC & Partners
 * 
 * Visit https://code.google.com/p/gama-platform/ for license information and developers contact.
 * 
 * 
 **********************************************************************************************/
package msi.gaml.statements;

import msi.gama.common.interfaces.*;
import msi.gama.precompiler.GamlAnnotations.doc;
import msi.gama.precompiler.GamlAnnotations.example;
import msi.gama.precompiler.GamlAnnotations.facet;
import msi.gama.precompiler.GamlAnnotations.facets;
import msi.gama.precompiler.GamlAnnotations.inside;
import msi.gama.precompiler.GamlAnnotations.symbol;
import msi.gama.precompiler.GamlAnnotations.usage;
import msi.gama.precompiler.GamlAnnotations.validator;
import msi.gama.precompiler.*;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gaml.compilation.IDescriptionValidator;
import msi.gaml.descriptions.*;
import msi.gaml.expressions.IExpression;
import msi.gaml.operators.Cast;
import msi.gaml.species.ISpecies;
import msi.gaml.statements.DoStatement.DoValidator;
import msi.gaml.types.IType;

/**
 * Written by drogoul Modified on 7 févr. 2010
 * 
 * @todo Description
 * 
 */
@symbol(name = { IKeyword.DO, IKeyword.REPEAT }, kind = ISymbolKind.SINGLE_STATEMENT, with_sequence = true, with_scope = false, with_args = true)
@inside(kinds = { ISymbolKind.BEHAVIOR, ISymbolKind.SEQUENCE_STATEMENT }, symbols = IKeyword.CHART)
@facets(value = {
	@facet(name = IKeyword.ACTION, type = IType.ID, optional = false, doc = @doc("the name of an action or a primitive")),
	@facet(name = IKeyword.WITH, type = IType.MAP, optional = true, doc = @doc(value = "a map expression containing the parameters of the action")),
	@facet(name = IKeyword.INTERNAL_FUNCTION, type = IType.NONE, optional = true, internal = true),
	@facet(name = IKeyword.RETURNS, type = IType.NEW_TEMP_ID, optional = true, doc = @doc(deprecated = "declare a temporary variable and use assignement instead", value = "create a new variable and assign to it the result of the action")) }, omissible = IKeyword.ACTION)
@doc(value = "Allows the agent to execute an action or a primitive.  For a list of primitives available in every species, see this [BuiltIn161 page]; for the list of primitives defined by the different skills, see this [Skills161 page]. Finally, see this [Species161 page] to know how to declare custom actions.", usages = {
	@usage(value = "The simple syntax (when the action does not expect any argument and the result is not to be kept) is:", examples = { @example(value = "do name_of_action_or_primitive;", isExecutable = false) }),
	@usage(value = "In case the action expects one or more arguments to be passed, they are defined by using facets (enclosed tags or a map are now deprecated):", examples = { @example(value = "do name_of_action_or_primitive arg1: expression1 arg2: expression2;", isExecutable = false) }),
	@usage(value = "In case the result of the action needs to be made available to the agent, the action can be called with the agent calling the action (`self` when the agent itself calls the action) instead of `do`; the result should be assigned to a temporary variable:", examples = { @example(value = "type_returned_by_action result <- self name_of_action_or_primitive [];", isExecutable = false) }),
	@usage(value = "In case of an action expecting arguments and returning a value, the following syntax is used:", examples = { @example(value = "type_returned_by_action result <- self name_of_action_or_primitive [arg1::expression1, arg2::expression2];", isExecutable = false) }),
	@usage(value = "Deprecated uses: following uses of the `do` statement (still accepted) are now deprecated:", examples = {
		@example(value = "// Simple syntax: "),
		@example(value = "do action: name_of_action_or_primitive;", isExecutable = false),
		@example(""),
		@example(value = "// In case the result of the action needs to be made available to the agent, the `returns` keyword can be defined; the result will then be referred to by the temporary variable declared in this attribute:"),
		@example(value = "do name_of_action_or_primitive returns: result;", isExecutable = false),
		@example(value = "do name_of_action_or_primitive arg1: expression1 arg2: expression2 returns: result;", isExecutable = false),
		@example(value = "type_returned_by_action result <- name_of_action_or_primitive(self, [arg1::expression1, arg2::expression2]);", isExecutable = false),
		@example(""),
		@example(value = "// In case the result of the action needs to be made available to the agent"),
		@example(value = "let result <- name_of_action_or_primitive(self, []);", isExecutable = false),
		@example(""),
		@example(value = "// In case the action expects one or more arguments to be passed, they can also be defined by using enclosed `arg` statements, or the `with` facet with a map of parameters:"),
		@example(value = "do name_of_action_or_primitive with: [arg1::expression1, arg2::expression2];", isExecutable = false),
		@example(value = "", isExecutable = false), @example(value = "or", isExecutable = false),
		@example(value = "", isExecutable = false),
		@example(value = "do name_of_action_or_primitive {", isExecutable = false),
		@example(value = "     arg arg1 value: expression1;", isExecutable = false),
		@example(value = "     arg arg2 value: expression2;", isExecutable = false),
		@example(value = "     ...", isExecutable = false), @example(value = "}", isExecutable = false) }) })
@validator(DoValidator.class)
public class DoStatement extends AbstractStatementSequence implements IStatement.WithArgs {

	public static class DoValidator implements IDescriptionValidator {

		/**
		 * Method validate()
		 * @see msi.gaml.compilation.IDescriptionValidator#validate(msi.gaml.descriptions.IDescription)
		 */
		@Override
		public void validate(final IDescription desc) {
			final String action = desc.getFacets().getLabel(ACTION);
			final SpeciesDescription sd = desc.getSpeciesContext();
			if ( sd == null ) { return; }
			// TODO What about actions defined in a macro species (not the global one, which is filtered before) ?
			if ( !sd.hasAction(action) ) {
				desc.error("Action " + action + " does not exist in " + sd.getName(), IGamlIssue.UNKNOWN_ACTION,
					ACTION, action, sd.getName());
			}
		}

	}

	Arguments args;
	String returnString;
	final IExpression function;

	public DoStatement(final IDescription desc) {
		super(desc);
		returnString = getLiteral(IKeyword.RETURNS);
		function = getFacet(IKeyword.INTERNAL_FUNCTION);
		setName(getLiteral(IKeyword.ACTION));
	}

	@Override
	public void enterScope(final IScope scope) {
		if ( returnString != null ) {
			scope.addVarWithValue(returnString, null);
		}
		super.enterScope(scope);
	}

	@Override
	public void setFormalArgs(final Arguments args) {
		this.args = args;
	}

	@Override
	public Object privateExecuteIn(final IScope scope) throws GamaRuntimeException {
		final ISpecies context = scope.getAgentScope().getSpecies();
		final IStatement.WithArgs executer = context.getAction(name);
		Object result = null;
		if ( executer != null ) {
			executer.setRuntimeArgs(args);
			result = executer.executeOn(scope);
		} else if ( function != null ) {
			result = function.value(scope);
		}
		if ( returnString != null ) {
			scope.setVarValue(returnString, result);
		}
		return result;
	}

	@Override
	public void setRuntimeArgs(final Arguments args) {}

	// @Override
	// public IType getType() {
	// final StatementDescription executer = description.getSpeciesContext().getAction(name);
	// if ( executer != null ) {
	// return executer.getType();
	// } else if ( function != null ) { return function.getType(); }
	// return Types.NO_TYPE;
	// }

	// @Override
	// public IType getContentType() {
	// final StatementDescription executer = description.getSpeciesContext().getAction(name);
	// if ( executer != null ) {
	// return executer.getContentType();
	// } else if ( function != null ) { return function.getContentType(); }
	// return Types.NO_TYPE;
	// }
	//
	// @Override
	// public IType getKeyType() {
	// final StatementDescription executer = description.getSpeciesContext().getAction(name);
	// if ( executer != null ) {
	// return executer.getKeyType();
	// } else if ( function != null ) { return function.getKeyType(); }
	// return Types.NO_TYPE;
	// }

//	@Override
//	public Double computePertinence(final IScope scope) throws GamaRuntimeException {
//		final ISpecies context = scope.getAgentScope().getSpecies();
//		final IStatement.WithArgs executer = context.getAction(name);
//		if ( executer.getPertinence() != null ) { return Cast.asFloat(scope, executer.getPertinence().value(scope)); }
//		return 1.0;
//	}
}
