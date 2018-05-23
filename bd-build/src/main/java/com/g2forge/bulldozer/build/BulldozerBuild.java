package com.g2forge.bulldozer.build;

import java.util.Arrays;

import com.g2forge.alexandria.command.IArgsCommand;

public class BulldozerBuild implements IArgsCommand {
	public static void main(String[] args) throws Throwable {
		IArgsCommand.main(args, BulldozerBuild::new);
	}

	@Override
	public int invoke(String... args) throws Throwable {
		switch (args[0].toLowerCase()) {
			case "catalog":
				Catalog.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			case "create-project":
				CreateProject.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			case "create-prs":
				CreatePRs.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			case "release":
				Release.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			default:
				System.err.println("Unrecognized command \"" + args[0] + "\"!");
				return FAIL;
		}
		return SUCCESS;
	}
}
