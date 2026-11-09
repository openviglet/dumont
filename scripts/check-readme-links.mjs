// D254: no README in the repository points at Dumont's old homes. The product site and its
// documentation live at dumont.viglet.org; www.viglet.org/dumont is a copy whose
// canonical names that site, and docs.viglet.org/dumont and openviglet.github.io/dumont only
// redirect. A README that links
// either keeps the old address alive for readers, crawlers and AI assistants.
//
//   node scripts/check-readme-links.mjs
//
// Exits 1, naming each file and line, when one does.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join( fileURLToPath( import.meta.url ), '..', '..' );
const skip = new Set( [ 'node_modules', '.git', 'dist', 'target', 'build', 'vendor' ] );
const refused = /\b(?:(?:www\.|docs\.)?viglet\.org|openviglet\.github\.io)\/dumont\b/i;

/**
 * Every README under a folder: README.md, README.txt, readme.txt, whatever the case.
 *
 * @param {string} dir The folder.
 * @return {string[]} Their paths.
 */
function readmes( dir ) {
	return readdirSync( dir ).flatMap( ( name ) => {
		if ( skip.has( name ) ) {
			return [];
		}
		const path = join( dir, name );
		if ( statSync( path ).isDirectory() ) {
			return readmes( path );
		}
		return /^readme\.(?:md|txt)$/i.test( name ) ? [ path ] : [];
	} );
}

/**
 * The lines of some files that match a pattern, as `path:line: text`.
 *
 * @param {string[]} files The files.
 * @param {RegExp}   pattern What is refused.
 * @return {string[]} One entry per matching line.
 */
function offending( files, pattern ) {
	return files.flatMap( ( file ) =>
		readFileSync( file, 'utf8' )
			.split( '\n' )
			.flatMap( ( line, i ) =>
				pattern.test( line )
					? [ `${ relative( root, file ) }:${ i + 1 }: ${ line.trim() }` ]
					: []
			)
	);
}

/**
 * Every file under a folder whose name passes a test, skipping build output.
 *
 * @param {string} dir  The folder.
 * @param {RegExp} name What the file name must match.
 * @return {string[]} Their paths.
 */
function filesUnder( dir, name ) {
	return readdirSync( dir ).flatMap( ( entry ) => {
		if ( skip.has( entry ) ) {
			return [];
		}
		const path = join( dir, entry );
		if ( statSync( path ).isDirectory() ) {
			return filesUnder( path, name );
		}
		return name.test( entry ) ? [ path ] : [];
	} );
}

const found = offending( readmes( root ), refused );

// D352: the public site and its documentation link only destinations that answer today.
// docs.viglet.org is retired for every product (Viglet Turing's docs live at
// turing.viglet.org/docs/), and the old Dumont homes only redirect. The hub's
// /dumont/download/ and /dumont/release-notes/ are the exception: they exist on
// www.viglet.org alone, and the site sends visitors there instead of keeping a
// second release ledger.
const site = join( root, 'dumont-site' );
const siteRefused =
	/\bdocs\.viglet\.org\b|\bopenviglet\.github\.io\/(?:dumont|turing)\b|\bviglet\.org\/dumont(?!\/(?:download|release-notes)\/)\b/i;
const siteFound = offending(
	[
		...filesUnder( join( site, 'src' ), /\.(?:ts|tsx)$/ ),
		...filesUnder( join( site, 'docs', 'src' ), /\.(?:md|mdx|astro|ts)$/ ),
		...filesUnder( join( site, 'public' ), /\.(?:txt|html)$/ ),
	],
	siteRefused
);

if ( found.length || siteFound.length ) {
	if ( found.length ) {
		process.stderr.write(
			'README links to an old Dumont home; use https://dumont.viglet.org/ instead:\n' +
				found.map( ( line ) => `  ${ line }\n` ).join( '' )
		);
	}
	if ( siteFound.length ) {
		process.stderr.write(
			'The site links a retired destination (docs.viglet.org, or an old product home):\n' +
				siteFound.map( ( line ) => `  ${ line }\n` ).join( '' )
		);
	}
	process.exit( 1 );
}
process.stdout.write(
	'README and site links: none points at an old home or a retired domain.\n'
);
