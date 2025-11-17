#! /bin/bash
#echo EN_US
#echo Cast
#CAST_SQL=`cat character_en.sql`
#./dumont-jdbc.sh --encoding ISO-8859-9 --site 1 -t Character --include-type-in-id true -z 100 -d com.mysql.cj.jdbc.Driver -c jdbc:mysql://localhost/cinestamp  -q "${CAST_SQL}" -u cinestamp -p cinestamp

#echo Movie
#MOVIE_SQL=`cat movie_en.sql` 
#./dumont-jdbc.sh --encoding ISO-8859-9 --site 1 -t Movie --multi-valued-separator "," --multi-valued-field cast,persona,streaming,tv,genres --include-type-in-id true -z 100 -d com.mysql.cj.jdbc.Driver -c jdbc:mysql://localhost/cinestamp  -q "${MOVIE_SQL}" -u cinestamp -p cinestamp

echo PT_BR
#echo Cast
#CAST_SQL=`cat character_pt.sql`
#./dumont-jdbc.sh --encoding ISO-8859-9 --site 1 -t Character --include-type-in-id true -z 100 -d com.mysql.cj.jdbc.Driver -c jdbc:mysql://localhost/cinestamp  -q "${CAST_SQL}" -u cinestamp -p cinestamp

echo Movie
MOVIE_SQL=`cat movie_pt.sql` 
./dumont-jdbc.sh --class-name com.viglet.dumont.tool.ext.DumJDBCCustomSample --encoding ISO-8859-9  --site Sample -t Movie --multi-valued-separator "," --multi-valued-field cast,persona,streaming,tv,genres --include-type-in-id true -z 100 -d com.mysql.jdbc.Driver -c jdbc:mysql://localhost/cinestamp  -q "${MOVIE_SQL}" -u cinestamp -p cinestamp
