package com.ssafy.trip.util;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Eclipse 모듈 루트 또는 그 상위 폴더에서 실행해도 프로젝트 리소스를 찾는다.
 */
public final class ProjectResourceLocator {

	public static final String PROJECT_DIRECTORY_PROPERTY = "enjoytrip.project.dir";
	public static final String PROJECT_DIRECTORY_ENVIRONMENT = "ENJOYTRIP_PROJECT_DIR";

	private ProjectResourceLocator() {
	}

	public static Path resolve(String first, String... more) {
		Path relativePath = Paths.get(first, more);
		for (Path projectRoot : projectRootCandidates()) {
			Path candidate = projectRoot.resolve(relativePath).normalize();
			if (Files.exists(candidate)) {
				return candidate;
			}
		}
		return defaultProjectRoot().resolve(relativePath).normalize();
	}

	private static List<Path> projectRootCandidates() {
		List<Path> candidates = new ArrayList<Path>();
		String configured = System.getProperty(PROJECT_DIRECTORY_PROPERTY);
		if (configured == null || configured.trim().isEmpty()) {
			configured = System.getenv(PROJECT_DIRECTORY_ENVIRONMENT);
		}
		if (configured != null && !configured.trim().isEmpty()) {
			addCandidate(candidates, Paths.get(configured.trim()));
		}

		Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
		addWithModuleChild(candidates, workingDirectory);
		addAncestors(candidates, workingDirectory);

		CodeSource codeSource = ProjectResourceLocator.class.getProtectionDomain().getCodeSource();
		if (codeSource != null) {
			try {
				Path codeLocation = Paths.get(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
				addWithModuleChild(candidates, Files.isDirectory(codeLocation)
						? codeLocation : codeLocation.getParent());
				addAncestors(candidates, codeLocation);
			} catch (URISyntaxException e) {
				// 작업 디렉터리 후보를 계속 사용한다.
			}
		}
		return candidates;
	}

	private static Path defaultProjectRoot() {
		List<Path> candidates = projectRootCandidates();
		for (Path candidate : candidates) {
			if (Files.isDirectory(candidate.resolve("res")) || Files.isDirectory(candidate.resolve("src"))) {
				return candidate;
			}
		}
		return Paths.get("").toAbsolutePath().normalize();
	}

	private static void addAncestors(List<Path> candidates, Path start) {
		Path current = Files.isDirectory(start) ? start : start.getParent();
		for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
			addWithModuleChild(candidates, current);
		}
	}

	private static void addWithModuleChild(List<Path> candidates, Path directory) {
		if (directory == null) {
			return;
		}
		addCandidate(candidates, directory);
		addCandidate(candidates, directory.resolve("EnjoyTrip"));
	}

	private static void addCandidate(List<Path> candidates, Path candidate) {
		Path normalized = candidate.toAbsolutePath().normalize();
		if (!candidates.contains(normalized)) {
			candidates.add(normalized);
		}
	}
}
