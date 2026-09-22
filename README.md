aznd (Ascend)
by ashr (abinivesh)

the plan:

open-source all in one lifestyle app
no payment wall, reliable, all functions work offline, link to your own cloud for data storage
no LLM API, local model

-nutrition tracker + food quality score system (local AI that measures food based on images + manual input)
-supplement tracker
-run tracker (gps)
-workout tracker + analyzer (manual input)
-calisthenics progression
-sleep tracker (manual input)
-stat card generator (streak, personal best)
-exportable data (csv)
-themes/skins


data sets:
-nutrition5k (images and details of western food)
https://github.com/google-research-datasets/Nutrition5k
-indian-food-nutritional-values-dataset (2025) (no images)
https://www.kaggle.com/datasets/batthulavinay/indian-food-nutrition
-khana
https://arxiv.org/html/2509.06006v1

architecture:

    -nutrition tracker
        -use segmentation (YOLO seg) and classification to isolate each food
        -MiDas depth estimation with reference object
        -a regressor/CNN for weight
        -another option to use 2 angles for more accuracy
        -tracks micros,macros from a nutrition data set
        -allows user intervention
        -pretrained python model executing through compiled language
        -adds the micro nutrients from supplements too
        -meals can be saved and resused, avoids redundancy

    -supplement tracker
        -uses supplement details, frequency and quantity
        -has option for cycles (2 weeks on, 2 weeks off - for supplements like Boron)
        -adds micro details to final micro calculator

    -run tracker
    

progress:

    17/08/2026:
        -identified desired data sets, planned the program flow

    18/08/2026:
        -attempted to process the data, realized i might need additional data sets for indian foods as the nutrition5k data set only has western fastfoods.
        -indian-food-and-nutrition-2025 (kaggle) has data for nutrition but lacks images for classification / volume estimation. i will have to train the model with pictures from another dataset and feed the output to nutrition calculator
        -khana has images for classification / volume estimation.

    17/08/2026:
        -designed logo and banner art
        -started work on workout tracker app
        -implemented basic workout routine logic

    18/08/2026:
        -added picture feature to save with workouts
        -added share feature

    19/08/2026:
        -improvement in UI and User experiences
        -added previous session statistics on running workout screen for progressive overload

    20/08/2026:
        -added statistics and graph for individual set and handled what to do on deletion
        -laid the framework for a streak tracker

    21/08/2026:
        -completed streak manager app with day-saving, pictures and graphical calendar views

    22/08/2026: