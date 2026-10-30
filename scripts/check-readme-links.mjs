// D254: no README in the repository points at Dumont's old homes. The product site and its
// documentation live at openviglet.github.io/dumont; www.viglet.org/dumont is a copy whose
// canonical names that site, and docs.viglet.org/dumont only redirects. A README that links
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
const refused = /\b(?:www\.|docs\.)?viglet\.org\/dumont\b/i;

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

const found = readmes( root ).flatMap( ( file ) =>
	readFileSync( file, 'utf8' )
		.split( '\n' )
		.flatMap( ( line, i ) =>
			refused.test( line )
				? [ `${ relative( root, file ) }:${ i + 1 }: ${ line.trim() }` ]
				: []
		)
);

if ( found.length ) {
	process.stderr.write(
		'README links to an old Dumont home; use https://openviglet.github.io/dumont/ instead:\n' +
			found.map( ( line ) => `  ${ line }\n` ).join( '' )
	);
	process.exit( 1 );
}
process.stdout.write( 'README links: none points at an old Dumont home.\n' );
