package com.g2forge.bulldozer.build;

import com.g2forge.alexandria.command.command.DispatchCommand;
import com.g2forge.alexandria.command.command.IStructuredCommand;

public class BulldozerBuild implements IStructuredCommand {
	public static void main(String[] args) throws Throwable {
		final DispatchCommand.ManualBuilder builder = new DispatchCommand.ManualBuilder();
		builder.command(Catalog.COMMAND_FACTORY, "catalog");
		builder.command(CreateProject.COMMAND_FACTORY, "create-project");
		builder.command(CreatePRs.COMMAND_FACTORY, "create-prs");
		builder.command(CleanupPRs.COMMAND_FACTORY, "cleanup-prs");
		builder.command(Release.COMMAND_FACTORY, "release");
		builder.main(args);
	}
}
